package com.quickbite.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Incoming orders as the restaurant sees them (snake_case JSON, like every QuickBite API). */
public final class RestaurantOrderDtos {

    private RestaurantOrderDtos() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RestaurantOrderResponse(
            UUID orderId,
            UUID restaurantId,
            UUID customerId,
            String status,
            String reason,
            BigDecimal totalAmount,
            String deliveryAddress,
            List<Line> items,
            Instant placedAt,
            Instant updatedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Line(UUID menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
    }

    /** Body of a rejection: the reason is passed on to order-service and shown to the customer. */
    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
