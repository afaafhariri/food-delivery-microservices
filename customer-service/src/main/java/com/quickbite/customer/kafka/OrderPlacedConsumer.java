package com.quickbite.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickbite.customer.model.OrderHistory;
import com.quickbite.customer.service.OrderHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

            UUID customerId = UUID.fromString(json.get("customerId").asText());
            UUID orderId = UUID.fromString(json.get("orderId").asText());
            UUID restaurantId = json.has("restaurantId") && !json.get("restaurantId").isNull()
                    ? UUID.fromString(json.get("restaurantId").asText())
                    : null;
            String deliveryAddress = json.has("deliveryAddress") ? json.get("deliveryAddress").asText() : null;
            LocalDateTime orderDate = json.has("orderDate")
                    ? LocalDateTime.parse(json.get("orderDate").asText())
                    : LocalDateTime.now();

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
