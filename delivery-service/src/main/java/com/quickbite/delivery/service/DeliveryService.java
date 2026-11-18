package com.quickbite.delivery.service;

import com.quickbite.delivery.domain.CancelledOrder;
import com.quickbite.delivery.domain.CancelledOrderRepository;
import com.quickbite.delivery.domain.Delivery;
import com.quickbite.delivery.domain.DeliveryRepository;
import com.quickbite.delivery.domain.DeliveryStatus;
import com.quickbite.delivery.domain.Driver;
import com.quickbite.delivery.domain.DriverRepository;
import com.quickbite.delivery.dto.DeliveryDtos.DeliveryResponse;
import com.quickbite.delivery.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
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
    private final ApplicationEventPublisher events;

    /**
     * Creates the delivery for a newly placed order. Idempotent on {@code orderId}, so Kafka
     * redeliveries are harmless; driver claiming is race-free ({@code FOR UPDATE SKIP LOCKED}).
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
            log.warn("no available driver for order {}", orderId);
            return Optional.empty();
        }
        Driver driver = next.get();
        driver.setAvailable(false);

        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setDriver(driver);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        deliveries.save(delivery);

        events.publishEvent(new DeliveryStatusChanged(delivery.getId(), orderId, driver.getId(),
                DeliveryStatus.ASSIGNED.name()));
        log.info("delivery {} assigned order={} driver={}", delivery.getId(), orderId, driver.getId());
        return Optional.of(delivery);
    }

    /**
     * Handles {@code order.cancelled}. An ASSIGNED delivery is cancelled and its driver freed
     * (announced as {@code delivery.status.updated} CANCELLED). A delivery that is already
     * picked up, en route or delivered is left alone: the food is on its way. Idempotent.
     */
    @Transactional
    public void cancelForOrder(UUID orderId) {
        if (!cancelledOrders.existsById(orderId)) {
            cancelledOrders.save(new CancelledOrder(orderId));
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
        return deliveries.findWithDriverById(id)
                .map(DeliveryService::toResponse)
                .orElseThrow(() -> ApiException.notFound("delivery not found"));
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(UUID orderId) {
        return deliveries.findByOrderId(orderId)
                .map(DeliveryService::toResponse)
                .orElseThrow(() -> ApiException.notFound("delivery not found for order"));
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(UUID driverId, String rawStatus) {
        DeliveryStatus status = rawStatus == null || rawStatus.isBlank() ? null
                : DeliveryStatus.parse(rawStatus)
                        .orElseThrow(() -> ApiException.badRequest("invalid delivery status: " + rawStatus));
        Specification<Delivery> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(2);
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
        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw ApiException.badRequest("delivery was cancelled");
        }

        delivery.setStatus(status);
        Driver driver = delivery.getDriver();
        if ((status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED) && driver != null) {
            driver.setAvailable(true);
        }
        deliveries.saveAndFlush(delivery);

        events.publishEvent(new DeliveryStatusChanged(delivery.getId(), delivery.getOrderId(),
                driver != null ? driver.getId() : null, status.name()));
        return toResponse(delivery);
    }

    static DeliveryResponse toResponse(Delivery d) {
        Driver driver = d.getDriver();
        return new DeliveryResponse(d.getId(), d.getOrderId(),
                driver != null ? driver.getId() : null,
                driver != null ? DriverService.toResponse(driver) : null,
                d.getStatus().name(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
