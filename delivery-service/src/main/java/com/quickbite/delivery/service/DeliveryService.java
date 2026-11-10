package com.quickbite.delivery.service;

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

        delivery.setStatus(status);
        Driver driver = delivery.getDriver();
        if (status == DeliveryStatus.DELIVERED && driver != null) {
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
