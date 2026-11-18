package com.quickbite.order.kafka;

import com.quickbite.messaging.EventHeaders;
import com.quickbite.messaging.Inbox;
import com.quickbite.messaging.NonRetryableEventException;
import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import com.quickbite.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies status changes announced by delivery-service and restaurant-service.
 * <p>
 * Each listener runs in one transaction with its inbox entry, so a redelivered event is
 * skipped and a failed one is retried (then sent to {@code <topic>.DLT}). Malformed events
 * go to the DLT immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusListeners {

    static final String GROUP = "order-service-group";

    private final OrderRepository repository;
    private final OrderService orders;
    private final Inbox inbox;
    private final ObjectMapper json;

    record DeliveryStatusEvent(UUID orderId, String status) {
    }

    record RestaurantResponseEvent(UUID orderId, String status, String reason) {
    }

    @Transactional
    @KafkaListener(topics = "delivery.status.updated", groupId = GROUP)
    public void onDeliveryStatus(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        DeliveryStatusEvent event = parse(record.value(), DeliveryStatusEvent.class);
        if (event.orderId() == null || event.status() == null) {
            throw new NonRetryableEventException("delivery.status.updated without orderId or status: " + record.value());
        }
        // Delivery has its own lifecycle; only states that exist on an order are mirrored.
        OrderStatus mapped = switch (event.status()) {
            case "PICKED_UP", "EN_ROUTE" -> OrderStatus.PICKED_UP;
            case "DELIVERED" -> OrderStatus.DELIVERED;
            default -> null;
        };
        if (mapped != null) {
            apply(event.orderId(), mapped, "delivery");
        }
    }

    @Transactional
    @KafkaListener(topics = "restaurant.order.response", groupId = GROUP)
    public void onRestaurantResponse(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        RestaurantResponseEvent event = parse(record.value(), RestaurantResponseEvent.class);
        if (event.orderId() == null) {
            throw new NonRetryableEventException("restaurant.order.response without orderId: " + record.value());
        }
        if ("CONFIRMED".equals(event.status())) {
            apply(event.orderId(), OrderStatus.CONFIRMED, "restaurant");
        } else if (!orders.cancelByRestaurant(event.orderId(), event.reason())) {
            // Through OrderService, so the cancellation is announced on order.cancelled.
            log.warn("restaurant event for unknown order {}", event.orderId());
        }
    }

    private void apply(UUID orderId, OrderStatus status, String source) {
        int updated = repository.updateStatus(orderId, status, Instant.now());
        if (updated == 0) {
            log.warn("{} event for unknown order {}", source, orderId);
        } else {
            log.info("order {} -> {} (from {} event)", orderId, status, source);
        }
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return json.readValue(payload, type);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new NonRetryableEventException("malformed " + type.getSimpleName() + " payload: " + payload, e);
        }
    }
}
