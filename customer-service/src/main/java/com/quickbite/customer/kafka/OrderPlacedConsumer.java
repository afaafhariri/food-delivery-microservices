package com.quickbite.customer.kafka;

import com.quickbite.customer.model.OrderHistory;
import com.quickbite.customer.service.OrderHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedConsumer {

    private final OrderHistoryService orderHistoryService;
    private final NotificationProducer notificationProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.placed", groupId = "customer-service-group")
    public void consume(String message) {
        try {
            log.info("Received order.placed event: {}", message);

            JsonNode json = objectMapper.readTree(message);

            UUID customerId = UUID.fromString(json.get("customerId").asString());
            UUID orderId = UUID.fromString(json.get("orderId").asString());
            UUID restaurantId = json.hasNonNull("restaurantId")
                    ? UUID.fromString(json.get("restaurantId").asString())
                    : null;
            String deliveryAddress = json.hasNonNull("deliveryAddress") ? json.get("deliveryAddress").asString() : null;
            LocalDateTime orderDate = json.hasNonNull("timestamp")
                    ? LocalDateTime.ofInstant(Instant.parse(json.get("timestamp").asString()), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);

            OrderHistory orderHistory = OrderHistory.builder()
                    .customerId(customerId)
                    .orderId(orderId)
                    .restaurantId(restaurantId)
                    .deliveryAddress(deliveryAddress)
                    .orderDate(orderDate)
                    .build();

            orderHistoryService.save(orderHistory);
            log.info("Saved order history for customer {} - order {}", customerId, orderId);

            notificationProducer.sendOrderPlacedNotification(customerId, orderId);
        } catch (Exception e) {
            log.error("Failed to process order.placed event: {}", message, e);
        }
    }
}
