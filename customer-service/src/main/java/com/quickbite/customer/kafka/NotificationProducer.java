package com.quickbite.customer.kafka;

import com.quickbite.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** {@code customer.notification}, written to the transactional outbox (call it inside a transaction). */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private static final String TOPIC = "customer.notification";

    private final Outbox outbox;

    public void sendOrderPlacedNotification(UUID customerId, UUID orderId) {
        outbox.publish(TOPIC, customerId.toString(), Map.of(
                "customerId", customerId.toString(),
                "message", "Your order " + orderId + " has been placed successfully.",
                "type", "ORDER_PLACED",
                "timestamp", LocalDateTime.now().toString()));
        log.info("Queued notification for customer {} - order {}", customerId, orderId);
    }
}
