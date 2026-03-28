package com.quickbite.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantResponse {

    private UUID id;
    private String name;
    private String address;
    private String cuisineType;
    private String operatingHours;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
