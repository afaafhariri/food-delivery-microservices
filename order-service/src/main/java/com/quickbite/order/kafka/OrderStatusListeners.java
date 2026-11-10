package com.quickbite.order.kafka;

import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** Applies status changes announced by delivery-service and restaurant-service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusListeners {

    private final OrderRepository repository;
    private final ObjectMapper json;

    record DeliveryStatusEvent(UUID orderId, String status) {
    }

    record RestaurantResponseEvent(UUID orderId, String status, String reason) {
    }

    @KafkaListener(topics = "delivery.status.updated", groupId = "order-service-group")
    public void onDeliveryStatus(String payload) {
        DeliveryStatusEvent event = parse(payload, DeliveryStatusEvent.class);
        if (event == null || event.orderId() == null || event.status() == null) {
            return;
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

    @KafkaListener(topics = "restaurant.order.response", groupId = "order-service-group")
    public void onRestaurantResponse(String payload) {
        RestaurantResponseEvent event = parse(payload, RestaurantResponseEvent.class);
        if (event == null || event.orderId() == null) {
            return;
        }
        OrderStatus mapped = "CONFIRMED".equals(event.status()) ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
        apply(event.orderId(), mapped, "restaurant");
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
            log.error("dropping malformed {} payload: {}", type.getSimpleName(), payload);
            return null;
        }
    }
}
