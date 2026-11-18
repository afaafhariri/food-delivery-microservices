package com.quickbite.customer.kafka;

import com.quickbite.customer.model.OrderHistory;
import com.quickbite.customer.service.OrderHistoryService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Records order history and queues the customer notification for every placed order.
 * <p>
 * Inbox entry, history row and notification event share one transaction: each order is
 * recorded and announced exactly once, even when Kafka redelivers {@code order.placed}.
 * Database errors are retried and then sent to {@code order.placed.DLT}; malformed events
 * go to the DLT at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedConsumer {

    static final String GROUP = "customer-service-group";

    private final OrderHistoryService orderHistoryService;
    private final NotificationProducer notificationProducer;
    private final Inbox inbox;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.placed", groupId = GROUP)
    public void consume(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        String message = record.value();
        log.info("Received order.placed event: {}", message);

        OrderHistory orderHistory;
        try {
            JsonNode json = objectMapper.readTree(message);
            UUID customerId = UUID.fromString(json.required("customerId").asString());
            UUID orderId = UUID.fromString(json.required("orderId").asString());
            UUID restaurantId = json.hasNonNull("restaurantId")
                    ? UUID.fromString(json.get("restaurantId").asString())
                    : null;
            String deliveryAddress = json.hasNonNull("deliveryAddress") ? json.get("deliveryAddress").asString() : null;
            LocalDateTime orderDate = json.hasNonNull("timestamp")
                    ? LocalDateTime.ofInstant(Instant.parse(json.get("timestamp").asString()), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);
            orderHistory = OrderHistory.builder()
                    .customerId(customerId)
                    .orderId(orderId)
                    .restaurantId(restaurantId)
                    .deliveryAddress(deliveryAddress)
                    .orderDate(orderDate)
                    .build();
        } catch (JacksonException | IllegalArgumentException | java.time.DateTimeException e) {
            throw new NonRetryableEventException("malformed order.placed payload: " + message, e);
        }

        orderHistoryService.save(orderHistory);
        log.info("Saved order history for customer {} - order {}", orderHistory.getCustomerId(), orderHistory.getOrderId());

        notificationProducer.sendOrderPlacedNotification(orderHistory.getCustomerId(), orderHistory.getOrderId());
    }
}
