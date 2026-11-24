package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.CancelledOrder;
import com.quickbite.delivery.domain.CancelledOrderRepository;
import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.domain.WaitingOrder;
import com.quickbite.delivery.domain.WaitingOrderRepository;
import com.quickbite.delivery.dto.DeliveryDtos.DeliveryResponse;
import com.quickbite.delivery.dto.DeliveryDtos.WaitingOrderResponse;
import com.quickbite.delivery.web.ApiException;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.Roles;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final DriverRepository drivers;
    private final CancelledOrderRepository cancelledOrders;
    private final WaitingOrderRepository waitingOrders;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;

    /**
     * Creates the delivery for a newly placed order. Idempotent on {@code orderId}, so Kafka
     * redeliveries are harmless; driver claiming is race-free ({@code FOR UPDATE SKIP LOCKED}).
     * If every driver is busy the order goes on the waiting list instead, and gets the next
     * driver that becomes free ({@link #assignOldestWaiting()}).
     */
    @Transactional
    public Optional<Delivery> assign(UUID orderId) {
        if (deliveries.existsByOrderId(orderId)) {
            log.info("delivery already exists for order {}, skipping", orderId);
            return Optional.empty();
        }
        if (cancelledOrders.existsById(orderId)) {
            log.info("order {} was cancelled before it reached delivery, not assigning a driver", orderId);
            return Optional.empty();
        }
        Optional<Driver> next = drivers.lockNextAvailable();
        if (next.isEmpty()) {
            waitingOrders.enqueue(orderId);
            // A driver freed while this transaction runs cannot see the new row yet, so look
            // again once it commits (see WaitingOrderDispatcher).
            events.publishEvent(new RetryWaitingOrders("order " + orderId + " is waiting"));
            log.warn("no available driver for order {}, it waits for the next free one", orderId);
            return Optional.empty();
        }
        return Optional.of(createDelivery(orderId, next.get()));
    }

    /**
     * Gives the longest-waiting order a free driver, in a transaction of its own. Both are
     * claimed with {@code FOR UPDATE SKIP LOCKED}, so instances running this at the same time
     * take different orders and different drivers; the unique {@code deliveries.order_id}
     * stops an order from ever getting two deliveries.
     *
     * @return true if the waiting list changed (an order was assigned, or dropped because it was
     *         cancelled or already has a delivery), so calling again may do more; false if no
     *         order is waiting or no driver is free
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignOldestWaiting() {
        Optional<WaitingOrder> oldest = waitingOrders.lockOldest();
        if (oldest.isEmpty()) {
            return false;
        }
        UUID orderId = oldest.get().getOrderId();
        if (cancelledOrders.existsById(orderId) || deliveries.existsByOrderId(orderId)) {
            waitingOrders.remove(orderId);
            log.info("order {} no longer needs a driver, removed from the waiting list", orderId);
            return true;
        }
        Optional<Driver> next = drivers.lockNextAvailable();
        if (next.isEmpty()) {
            return false;
        }
        waitingOrders.remove(orderId);
        createDelivery(orderId, next.get());
        return true;
    }

    /** The orders waiting for a driver, oldest first (admins only). */
    @Transactional(readOnly = true)
    public List<WaitingOrderResponse> waiting() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("only admins can list waiting orders");
        }
        return waitingOrders.findAllByOrderByWaitingSinceAscOrderIdAsc().stream()
                .map(w -> new WaitingOrderResponse(w.getOrderId(), w.getWaitingSince()))
                .toList();
    }

    private Delivery createDelivery(UUID orderId, Driver driver) {
        driver.setAvailable(false);

        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setDriver(driver);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        deliveries.save(delivery);

        events.publishEvent(new DeliveryStatusChanged(delivery.getId(), orderId, driver.getId(),
                DeliveryStatus.ASSIGNED.name()));
        log.info("delivery {} assigned order={} driver={}", delivery.getId(), orderId, driver.getId());
        return delivery;
    }

    /**
     * Handles {@code order.cancelled}. An ASSIGNED delivery is cancelled and its driver freed
     * (announced as {@code delivery.status.updated} CANCELLED). A delivery that is already
     * picked up, en route or delivered is left alone: the food is on its way. An order still
     * waiting for a driver leaves the waiting list. Idempotent.
     */
    @Transactional
    public void cancelForOrder(UUID orderId) {
        if (!cancelledOrders.existsById(orderId)) {
            cancelledOrders.save(new CancelledOrder(orderId));
        }
        // Before looking for the delivery: if assignOldestWaiting() has this order claimed, the
        // delete waits for it to commit, and the lookup below then finds the new delivery.
        if (waitingOrders.remove(orderId) > 0) {
            log.info("order {} cancelled while waiting for a driver", orderId);
        }
        Optional<Delivery> found = deliveries.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.info("order {} cancelled before a delivery was created", orderId);
            return;
        }
        Delivery delivery = found.get();
        switch (delivery.getStatus()) {
            case ASSIGNED -> {
                Driver driver = delivery.getDriver();
                delivery.setStatus(DeliveryStatus.CANCELLED);
                if (driver != null) {
                    driver.setAvailable(true);
                }
                deliveries.saveAndFlush(delivery);
                events.publishEvent(new DeliveryStatusChanged(delivery.getId(), orderId,
                        driver != null ? driver.getId() : null, DeliveryStatus.CANCELLED.name()));
                if (driver != null) {
                    events.publishEvent(new RetryWaitingOrders("driver " + driver.getId() + " freed by a cancellation"));
                }
                log.info("delivery {} cancelled with order {}, driver {} is available again", delivery.getId(),
                        orderId, driver != null ? driver.getId() : null);
            }
            case CANCELLED -> log.info("delivery {} already cancelled", delivery.getId());
            default -> log.warn("order {} cancelled but delivery {} is already {}; leaving it",
                    orderId, delivery.getId(), delivery.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(UUID id) {
        Delivery delivery = deliveries.findWithDriverById(id)
                .orElseThrow(() -> ApiException.notFound("delivery not found"));
        requireAssignedDriverOrAdmin(delivery);
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(UUID orderId) {
        Delivery delivery = deliveries.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("delivery not found for order"));
        requireAssignedDriverOrAdmin(delivery);
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(UUID driverId, String rawStatus) {
        // Admins see every delivery; a driver only the deliveries assigned to them.
        String onlyDriverUser;
        if (currentUser.isAdmin()) {
            onlyDriverUser = null;
        } else if (currentUser.hasRole(Roles.DRIVER)) {
            onlyDriverUser = currentUser.id();
        } else {
            throw new AccessDeniedException("only drivers and admins can list deliveries");
        }
        DeliveryStatus status = rawStatus == null || rawStatus.isBlank() ? null
                : DeliveryStatus.parse(rawStatus)
                        .orElseThrow(() -> ApiException.badRequest("invalid delivery status: " + rawStatus));
        Specification<Delivery> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(2);
            if (onlyDriverUser != null) {
                predicates.add(cb.equal(root.get("driver").get("userId"), onlyDriverUser));
            }
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driver").get("id"), driverId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return deliveries.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(DeliveryService::toResponse)
                .toList();
    }

    @Transactional
    public DeliveryResponse updateStatus(UUID id, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw ApiException.badRequest("status is required");
        }
        DeliveryStatus status = DeliveryStatus.parse(rawStatus)
                .orElseThrow(() -> ApiException.badRequest("invalid delivery status: " + rawStatus));
        Delivery delivery = deliveries.findWithDriverById(id)
                .orElseThrow(() -> ApiException.notFound("delivery not found"));
        requireAssignedDriverOrAdmin(delivery);
        if (!delivery.getStatus().canMoveTo(status)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "cannot move delivery from " + delivery.getStatus() + " to " + status);
        }
        if (delivery.getStatus() == status) {
            return toResponse(delivery);   // nothing changes, nothing to announce
        }

        delivery.setStatus(status);
        Driver driver = delivery.getDriver();
        boolean freesDriver = (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED) && driver != null;
        if (freesDriver) {
            driver.setAvailable(true);
        }
        deliveries.saveAndFlush(delivery);

        events.publishEvent(new DeliveryStatusChanged(delivery.getId(), delivery.getOrderId(),
                driver != null ? driver.getId() : null, status.name()));
        if (freesDriver) {
            events.publishEvent(new RetryWaitingOrders("driver " + driver.getId() + " freed: delivery " + status));
        }
        return toResponse(delivery);
    }

    /** HTTP access to a delivery: its assigned driver's account, or an admin. */
    private void requireAssignedDriverOrAdmin(Delivery delivery) {
        Driver driver = delivery.getDriver();
        currentUser.requireOwnerOrAdmin(driver != null ? driver.getUserId() : null);
    }

    static DeliveryResponse toResponse(Delivery d) {
        Driver driver = d.getDriver();
        return new DeliveryResponse(d.getId(), d.getOrderId(),
                driver != null ? driver.getId() : null,
                driver != null ? DriverService.toResponse(driver) : null,
                d.getStatus().name(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
