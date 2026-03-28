package com.quickbite.restaurant.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuEventProducer {

    private static final String TOPIC = "restaurant.menu.updated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishMenuEvent(UUID restaurantId, UUID itemId, MenuAction action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("restaurantId", restaurantId.toString());
        payload.put("itemId", itemId.toString());
        payload.put("action", action.name());

        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, restaurantId.toString(), message);
            log.info("Published menu event: restaurantId={}, itemId={}, action={}", restaurantId, itemId, action);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize menu event", e);
        }
    }

    public enum MenuAction {
        CREATED, UPDATED, DELETED
    }
}
