package com.quickbite.order.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload of {@code order.placed} (camelCase, wire-compatible with the Go producer). */
public record OrderPlacedEvent(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        List<Item> items,
        String deliveryAddress,
        BigDecimal totalAmount,
        Instant timestamp) {

    public record Item(UUID menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
    }
}
