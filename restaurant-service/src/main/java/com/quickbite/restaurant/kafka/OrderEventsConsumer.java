package com.quickbite.restaurant.kafka;

import com.quickbite.messaging.EventHeaders;
import com.quickbite.messaging.Inbox;
import com.quickbite.messaging.NonRetryableEventException;
import com.quickbite.restaurant.service.RestaurantOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

/**
 * Keeps each restaurant's list of incoming orders: {@code order.placed} adds one,
 * {@code order.cancelled} marks it cancelled. Each listener runs in one transaction with its inbox
 * entry (duplicates are skipped); malformed events go straight to {@code <topic>.DLT}.
 */
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    static final String GROUP = "restaurant-service-group";

    private final RestaurantOrderService orders;
    private final Inbox inbox;
    private final ObjectMapper json;

    @Transactional
    @KafkaListener(topics = "order.placed", groupId = GROUP)
    public void onOrderPlaced(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        try {
            JsonNode event = json.readTree(record.value());
            orders.recordPlaced(
                    UUID.fromString(event.required("orderId").asString()),
                    UUID.fromString(event.required("restaurantId").asString()),
                    event.hasNonNull("customerId") ? UUID.fromString(event.get("customerId").asString()) : null,
                    event.path("deliveryAddress").asString(null),
                    event.hasNonNull("totalAmount") ? event.get("totalAmount").decimalValue() : null,
                    event.has("items") ? json.writeValueAsString(event.get("items")) : "[]",
                    event.hasNonNull("timestamp") ? Instant.parse(event.get("timestamp").asString()) : Instant.now());
        } catch (JacksonException | IllegalArgumentException | DateTimeException e) {
            throw new NonRetryableEventException("malformed order.placed payload: " + record.value(), e);
        }
    }

    @Transactional
    @KafkaListener(topics = "order.cancelled", groupId = GROUP)
    public void onOrderCancelled(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        try {
            JsonNode event = json.readTree(record.value());
            orders.recordCancelled(
                    UUID.fromString(event.required("orderId").asString()),
                    UUID.fromString(event.required("restaurantId").asString()),
                    event.path("reason").asString(null));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new NonRetryableEventException("malformed order.cancelled payload: " + record.value(), e);
        }
    }
}
