package com.quickbite.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** REST contract of the order service (snake_case, unchanged from the Go implementation). */
public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * Either {@code address_id} (a saved customer address, resolved via gRPC) or a free-text
     * {@code delivery_address} must be supplied. Client-sent item names/prices are ignored:
     * the restaurant service is the pricing authority.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateOrderRequest(
            @NotNull UUID customerId,
            @NotNull UUID restaurantId,
            UUID addressId,
            @Size(max = 1000) String deliveryAddress,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull OrderItemRequest> items) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OrderItemRequest(@NotNull UUID menuItemId, @Min(1) int quantity) {
    }

    public record UpdateStatusRequest(String status) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OrderResponse(
            UUID id,
            UUID customerId,
            UUID restaurantId,
            String deliveryAddress,
            String status,
            BigDecimal totalAmount,
            Instant createdAt,
            Instant updatedAt,
            List<OrderItemResponse> items) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OrderItemResponse(
            UUID id,
            UUID orderId,
            UUID menuItemId,
            String menuItemName,
            int quantity,
            BigDecimal unitPrice) {
    }

    public record OrderPage(List<OrderResponse> data, long total, int page, int size) {
    }

    public record ErrorResponse(String message, int status) {
    }
}
