package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.CancelledOrderRepository;
import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.domain.WaitingOrder;
import com.quickbite.delivery.domain.WaitingOrderRepository;
import com.quickbite.delivery.dto.DeliveryDtos.UpdateDriverRequest;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.KeycloakJwtConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The waiting list: orders that found every driver busy get the next driver that becomes free. */
class WaitingOrdersTest {

    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final CancelledOrderRepository cancelledOrders = mock(CancelledOrderRepository.class);
    private final WaitingOrderRepository waitingOrders = mock(WaitingOrderRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DeliveryService service = new DeliveryService(deliveries, drivers, cancelledOrders, waitingOrders,
            events, new CurrentUser());

    private final UUID orderId = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // ── order.placed ─────────────────────────────────────────────────────────

    @Test
    void anOrderThatFindsEveryDriverBusyJoinsTheWaitingList() {
        when(drivers.lockNextAvailable()).thenReturn(Optional.empty());

        assertThat(service.assign(orderId)).isEmpty();

        verify(waitingOrders).enqueue(orderId);
        verify(deliveries, never()).save(any());
        // ...and asks for another look once the transaction commits, in case a driver was
        // freed meanwhile.
        verify(events).publishEvent(any(RetryWaitingOrders.class));
        verify(events, never()).publishEvent(any(DeliveryStatusChanged.class));
    }

    @Test
    void anOrderWithAFreeDriverIsAssignedAndNeverWaits() {
        Driver driver = driver(true);
        when(drivers.lockNextAvailable()).thenReturn(Optional.of(driver));

        assertThat(service.assign(orderId)).isPresent();

        assertThat(driver.isAvailable()).isFalse();
        verify(waitingOrders, never()).enqueue(any());
        verify(events, never()).publishEvent(any(RetryWaitingOrders.class));
    }

    @Test
    void aCancelledOrderNeverJoinsTheWaitingList() {
        when(cancelledOrders.existsById(orderId)).thenReturn(true);

        assertThat(service.assign(orderId)).isEmpty();

        verify(waitingOrders, never()).enqueue(any());
        verify(drivers, never()).lockNextAvailable();
    }

    // ── assignOldestWaiting ──────────────────────────────────────────────────

    @Test
    void theOldestWaitingOrderGetsTheFreeDriver() {
        Driver driver = driver(true);
        when(waitingOrders.lockOldest()).thenReturn(Optional.of(waiting(orderId)));
        when(drivers.lockNextAvailable()).thenReturn(Optional.of(driver));

        assertThat(service.assignOldestWaiting()).isTrue();

        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getDriver()).isSameAs(driver);
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(driver.isAvailable()).isFalse();
        verify(waitingOrders).remove(orderId);
        verify(events).publishEvent(any(DeliveryStatusChanged.class));
    }

    @Test
    void withNoFreeDriverTheOrderKeepsWaiting() {
        when(waitingOrders.lockOldest()).thenReturn(Optional.of(waiting(orderId)));
        when(drivers.lockNextAvailable()).thenReturn(Optional.empty());

        assertThat(service.assignOldestWaiting()).isFalse();

        verify(waitingOrders, never()).remove(any());
        verify(deliveries, never()).save(any());
    }

    @Test
    void anEmptyWaitingListNeedsNoDriver() {
        when(waitingOrders.lockOldest()).thenReturn(Optional.empty());

        assertThat(service.assignOldestWaiting()).isFalse();

        verify(drivers, never()).lockNextAvailable();
    }

    @Test
    void aWaitingOrderThatWasCancelledIsDroppedWithoutTakingADriver() {
        when(waitingOrders.lockOldest()).thenReturn(Optional.of(waiting(orderId)));
        when(cancelledOrders.existsById(orderId)).thenReturn(true);

        assertThat(service.assignOldestWaiting()).isTrue();   // the list changed: look again

        verify(waitingOrders).remove(orderId);
        verify(drivers, never()).lockNextAvailable();
        verify(deliveries, never()).save(any());
    }

    @Test
    void aWaitingOrderThatAlreadyHasADeliveryIsDropped() {
        when(waitingOrders.lockOldest()).thenReturn(Optional.of(waiting(orderId)));
        when(deliveries.existsByOrderId(orderId)).thenReturn(true);

        assertThat(service.assignOldestWaiting()).isTrue();

        verify(waitingOrders).remove(orderId);
        verify(drivers, never()).lockNextAvailable();
        verify(deliveries, never()).save(any());
    }

    // ── order.cancelled ──────────────────────────────────────────────────────

    @Test
    void cancellingAWaitingOrderTakesItOffTheList() {
        when(waitingOrders.remove(orderId)).thenReturn(1);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.empty());

        service.cancelForOrder(orderId);

        // The delete comes first: it waits for an assignment in progress, so the lookup that
        // follows sees that assignment's delivery and cancels it.
        InOrder order = inOrder(waitingOrders, deliveries);
        order.verify(waitingOrders).remove(orderId);
        order.verify(deliveries).findByOrderId(orderId);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void cancellingAnAssignedOrderOffersItsDriverToTheWaitingList() {
        Driver driver = driver(false);
        Delivery delivery = delivery(DeliveryStatus.ASSIGNED, driver);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));

        service.cancelForOrder(orderId);

        assertThat(driver.isAvailable()).isTrue();
        verify(events).publishEvent(any(RetryWaitingOrders.class));
    }

    // ── drivers becoming free over HTTP ──────────────────────────────────────

    @Test
    void aDeliveredDeliveryOffersItsDriverToTheWaitingList() {
        Driver driver = driver(false);
        driver.setUserId("driver-1");
        Delivery delivery = delivery(DeliveryStatus.EN_ROUTE, driver);
        when(deliveries.findWithDriverById(delivery.getId())).thenReturn(Optional.of(delivery));
        signIn("driver-1", "driver");

        service.updateStatus(delivery.getId(), "DELIVERED");

        assertThat(driver.isAvailable()).isTrue();
        verify(events).publishEvent(any(RetryWaitingOrders.class));
    }

    @Test
    void aStatusChangeThatKeepsTheDriverBusyDoesNotTouchTheWaitingList() {
        Driver driver = driver(false);
        driver.setUserId("driver-1");
        Delivery delivery = delivery(DeliveryStatus.ASSIGNED, driver);
        when(deliveries.findWithDriverById(delivery.getId())).thenReturn(Optional.of(delivery));
        signIn("driver-1", "driver");

        service.updateStatus(delivery.getId(), "PICKED_UP");

        verify(events, never()).publishEvent(any(RetryWaitingOrders.class));
    }

    @Test
    void aDriverSetAvailableIsOfferedToTheWaitingList() {
        DriverService driverService = new DriverService(drivers, new CurrentUser(), events);
        Driver driver = driver(false);
        when(drivers.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(drivers.saveAndFlush(driver)).thenReturn(driver);
        signIn("admin-1", "admin");

        driverService.update(driver.getId(), new UpdateDriverRequest(null, null, null, true));

        assertThat(driver.isAvailable()).isTrue();
        verify(events).publishEvent(any(RetryWaitingOrders.class));
    }

    @Test
    void aDriverWhoWasAlreadyAvailableOrGoesOffDutyIsNotOffered() {
        DriverService driverService = new DriverService(drivers, new CurrentUser(), events);
        Driver driver = driver(true);
        when(drivers.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(drivers.saveAndFlush(driver)).thenReturn(driver);
        signIn("admin-1", "admin");

        driverService.update(driver.getId(), new UpdateDriverRequest(null, null, null, true));
        driverService.update(driver.getId(), new UpdateDriverRequest(null, null, null, false));

        verify(events, never()).publishEvent(any());
    }

    // ── GET /api/deliveries/waiting ──────────────────────────────────────────

    @Test
    void onlyAdminsSeeTheWaitingList() {
        when(waitingOrders.findAllByOrderByWaitingSinceAscOrderIdAsc()).thenReturn(List.of(waiting(orderId)));

        signIn("admin-1", "admin");
        assertThat(service.waiting()).singleElement().satisfies(w -> assertThat(w.orderId()).isEqualTo(orderId));

        signIn("driver-1", "driver");
        assertThatThrownBy(service::waiting).isInstanceOf(AccessDeniedException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static WaitingOrder waiting(UUID orderId) {
        return new WaitingOrder(orderId, Instant.now());
    }

    private static Driver driver(boolean available) {
        Driver driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setAvailable(available);
        return driver;
    }

    private Delivery delivery(DeliveryStatus status, Driver driver) {
        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setOrderId(orderId);
        delivery.setStatus(status);
        delivery.setDriver(driver);
        return delivery;
    }

    private static void signIn(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    }
}
