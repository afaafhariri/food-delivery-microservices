package com.quickbite.restaurant.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderResponseProducer {

    private static final String TOPIC = "restaurant.order.response";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderResponse(UUID orderId, OrderStatus status, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("status", status.name());
        payload.put("reason", reason);
        payload.put("timestamp", LocalDateTime.now().toString());

        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, orderId.toString(), message);
            log.info("Published order response: orderId={}, status={}", orderId, status);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order response", e);
        }
    }

    public enum OrderStatus {
        CONFIRMED, REJECTED
    }
}
