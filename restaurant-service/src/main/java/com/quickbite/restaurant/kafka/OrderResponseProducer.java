package com.quickbite.restaurant.kafka;

import com.quickbite.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** {@code restaurant.order.response}, written to the transactional outbox (call it inside a transaction). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderResponseProducer {

    private static final String TOPIC = "restaurant.order.response";

    private final Outbox outbox;

    public void publishOrderResponse(UUID orderId, OrderStatus status, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("status", status.name());
        payload.put("reason", reason);
        payload.put("timestamp", LocalDateTime.now().toString());
        outbox.publish(TOPIC, orderId.toString(), payload);
        log.info("Queued order response: orderId={}, status={}", orderId, status);
    }

    public enum OrderStatus {
        CONFIRMED, REJECTED
    }
}
