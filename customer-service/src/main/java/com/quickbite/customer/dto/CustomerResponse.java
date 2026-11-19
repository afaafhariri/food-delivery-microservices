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
public class CustomerResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean active;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
