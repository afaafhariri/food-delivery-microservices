package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.domain.WaitingOrderRepository;
import com.quickbite.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The waiting list against a real Postgres (Liquibase schema, {@code SKIP LOCKED} claims, the
 * after-commit retry). Each test commits for real, as separate service instances would.
 * Skipped when Docker is not available.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WaitingOrdersIntegrationTest {

    /** Just the persistence layer and the two services; not the application class, which adds web security. */
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = Delivery.class)
    @EnableJpaRepositories(basePackageClasses = Delivery.class)
    @Import({DeliveryService.class, WaitingOrderDispatcher.class, CurrentUser.class})
    static class Persistence {
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withStartupAttempts(3);

    @Autowired
    DeliveryService service;

    @Autowired
    DeliveryRepository deliveries;

    @Autowired
    DriverRepository drivers;

    @Autowired
    WaitingOrderRepository waitingOrders;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE deliveries, drivers, waiting_orders, cancelled_orders").update();
    }

    @Test
    void concurrentDispatchersNeverShareAnOrderOrADriver() throws Exception {
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            // Distinct, increasing timestamps: the oldest orders must be served first.
            jdbc.sql("INSERT INTO waiting_orders (order_id, waiting_since) VALUES (?, now() - make_interval(secs => ?))")
                    .params(orderId, 100 - i).update();
        }
        List<UUID> freeDrivers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            freeDrivers.add(drivers.saveAndFlush(driver("driver " + i, true)).getId());
        }

        // Six "instances" race through the list at once.
        int workers = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            tasks.add(() -> {
                start.await();
                int assigned = 0;
                while (service.assignOldestWaiting()) {
                    assigned++;
                }
                return assigned;
            });
        }
        int total = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            List<Future<Integer>> results = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> result : results) {
                total += result.get();   // rethrows any failure, e.g. a duplicate order_id
            }
        }

        List<Delivery> all = deliveries.findAll();
        assertThat(total).isEqualTo(8);
        assertThat(all).hasSize(8);
        assertThat(all).extracting(Delivery::getOrderId).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(orders.subList(0, 8));
        assertThat(all).extracting(d -> d.getDriver().getId()).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(freeDrivers);
        assertThat(waitingOrders.findAll()).extracting(w -> w.getOrderId())
                .containsExactlyInAnyOrderElementsOf(orders.subList(8, 12));
        assertThat(drivers.findByAvailableOrderByCreatedAtAsc(true)).isEmpty();
    }

    @Test
    void anOrderThatWaitedGetsTheDriverFreedByACancellation() {
        Driver busy = drivers.saveAndFlush(driver("busy", true));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(service.assign(first)).isPresent();          // takes the only driver
        assertThat(service.assign(second)).isEmpty();           // waits
        assertThat(waitingOrders.existsById(second)).isTrue();

        // order.cancelled for the first order frees the driver; once that commits, the waiting
        // order gets them without any sweep.
        service.cancelForOrder(first);

        Delivery delivery = deliveries.findByOrderId(second).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(delivery.getDriver().getId()).isEqualTo(busy.getId());
        assertThat(waitingOrders.count()).isZero();
    }

    @Test
    void aCancelledWaitingOrderLeavesTheListAndNeverGetsADriver() {
        UUID orderId = UUID.randomUUID();
        assertThat(service.assign(orderId)).isEmpty();          // no drivers at all
        assertThat(waitingOrders.existsById(orderId)).isTrue();

        service.cancelForOrder(orderId);
        assertThat(waitingOrders.existsById(orderId)).isFalse();

        drivers.saveAndFlush(driver("late", true));
        assertThat(service.assignOldestWaiting()).isFalse();
        assertThat(deliveries.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void aStaleEntryForACancelledOrderIsDroppedByTheSweep() {
        // A cancellation that raced the enqueue: the row was written after the delete ran.
        UUID orderId = UUID.randomUUID();
        service.cancelForOrder(orderId);
        waitingOrders.enqueue(orderId);
        drivers.saveAndFlush(driver("free", true));

        assertThat(service.assignOldestWaiting()).isTrue();     // dropped, driver untouched
        assertThat(service.assignOldestWaiting()).isFalse();
        assertThat(waitingOrders.count()).isZero();
        assertThat(deliveries.count()).isZero();
        assertThat(drivers.findByAvailableOrderByCreatedAtAsc(true)).hasSize(1);
    }

    @Test
    void enqueueingTwiceKeepsOneEntry() {
        UUID orderId = UUID.randomUUID();
        waitingOrders.enqueue(orderId);
        waitingOrders.enqueue(orderId);
        assertThat(waitingOrders.count()).isEqualTo(1);
    }

    private static Driver driver(String name, boolean available) {
        Driver driver = new Driver();
        driver.setName(name);
        driver.setPhone("+94770000000");
        driver.setVehicleType("Motorcycle");
        driver.setAvailable(available);
        return driver;
    }
}
