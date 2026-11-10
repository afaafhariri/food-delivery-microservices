package com.quickbite.delivery.kafka;

import com.quickbite.delivery.service.DeliveryService;
import com.quickbite.delivery.service.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** Kafka edge of the delivery service: consumes {@code order.placed}, emits {@code delivery.status.updated}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEvents {

    static final String STATUS_TOPIC = "delivery.status.updated";

    private final DeliveryService deliveries;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    record OrderPlacedEvent(UUID orderId) {
    }

    /** Wire format unchanged from the Go producer. */
    record DeliveryStatusEvent(UUID deliveryId, UUID orderId, UUID driverId, String status, Instant timestamp) {
    }

    @KafkaListener(topics = "order.placed", groupId = "delivery-service-group")
    public void onOrderPlaced(String payload) {
        OrderPlacedEvent event;
        try {
            event = json.readValue(payload, OrderPlacedEvent.class);
        } catch (JacksonException | IllegalArgumentException e) {
            log.error("dropping malformed order.placed payload: {}", payload);
            return;
        }
        if (event.orderId() == null) {
            log.error("order.placed event missing orderId");
            return;
        }
        deliveries.assign(event.orderId());
    }

    @TransactionalEventListener
    public void publish(DeliveryStatusChanged change) {
        var event = new DeliveryStatusEvent(change.deliveryId(), change.orderId(), change.driverId(),
                change.status(), Instant.now());
        String key = change.deliveryId().toString();
        kafka.send(STATUS_TOPIC, key, json.writeValueAsString(event)).whenComplete((result, error) -> {
            if (error != null) {
                log.error("failed to publish {} deliveryId={}", STATUS_TOPIC, key, error);
            } else {
                log.info("published {} deliveryId={} status={}", STATUS_TOPIC, key, change.status());
            }
        });
    }
}
