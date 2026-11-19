package com.quickbite.order.kafka;

import com.quickbite.messaging.EventHeaders;
import com.quickbite.messaging.Inbox;
import com.quickbite.messaging.NonRetryableEventException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Logs {@code restaurant.menu.updated}. order-service needs nothing from it today: it prices every
 * order live through restaurant-service's gRPC {@code QuoteOrder}. This is where a menu cache
 * would be invalidated if order-service ever kept one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuUpdateListener {

    private final Inbox inbox;
    private final ObjectMapper json;

    @Transactional
    @KafkaListener(topics = "restaurant.menu.updated", groupId = OrderStatusListeners.GROUP)
    public void onMenuUpdated(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(OrderStatusListeners.GROUP, EventHeaders.eventId(record))) {
            return;
        }
        JsonNode event;
        try {
            event = json.readTree(record.value());
        } catch (JacksonException e) {
            throw new NonRetryableEventException("malformed restaurant.menu.updated payload: " + record.value(), e);
        }
        log.info("menu of restaurant {} changed: item {} {}", event.path("restaurantId").asString("?"),
                event.path("itemId").asString("?"), event.path("action").asString("?"));
    }
}
