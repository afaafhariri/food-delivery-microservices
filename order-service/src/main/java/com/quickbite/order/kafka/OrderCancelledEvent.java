package com.quickbite.order.kafka;

import java.time.Instant;
import java.util.UUID;

/** Payload of {@code order.cancelled} (camelCase, like {@code order.placed}). */
public record OrderCancelledEvent(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        String reason,
        Instant timestamp) {
}
