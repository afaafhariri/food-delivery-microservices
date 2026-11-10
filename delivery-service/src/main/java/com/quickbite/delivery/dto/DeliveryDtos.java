package com.quickbite.delivery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/** REST contract of the delivery service (snake_case, unchanged from the Go implementation). */
public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateDriverRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 50) String vehicleType) {
    }

    /** Partial update: null / blank fields are left unchanged. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateDriverRequest(
            @Size(max = 255) String name,
            @Size(max = 20) String phone,
            @Size(max = 50) String vehicleType,
            Boolean available) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DriverResponse(
            UUID id,
            String name,
            String phone,
            String vehicleType,
            boolean available,
            Instant createdAt,
            Instant updatedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryResponse(
            UUID id,
            UUID orderId,
            UUID driverId,
            DriverResponse driver,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UpdateDeliveryStatusRequest(String status) {
    }

    public record ErrorResponse(String message, int status) {
    }
}
