package com.quickbite.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private static final String TOPIC = "customer.notification";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendOrderPlacedNotification(UUID customerId, UUID orderId) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("customerId", customerId.toString());
            payload.put("message", "Your order " + orderId + " has been placed successfully.");
            payload.put("type", "ORDER_PLACED");
            payload.put("timestamp", LocalDateTime.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, customerId.toString(), json);
            log.info("Sent notification for customer {} - order {}", customerId, orderId);
        } catch (Exception e) {
            log.error("Failed to send notification for customer {} - order {}", customerId, orderId, e);
        }
    }
}
