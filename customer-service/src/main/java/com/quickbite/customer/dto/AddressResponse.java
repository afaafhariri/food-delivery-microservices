package com.quickbite.customer.dto;

import lombok.AllArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** JSON is snake_case, like every other QuickBite API. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

    private UUID id;
    private UUID customerId;
    private String label;
    private String addressLine;
    private String city;
    private String postalCode;
    private LocalDateTime createdAt;
}
