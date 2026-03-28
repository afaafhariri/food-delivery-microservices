package com.quickbite.customer.dto;

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
public class OrderHistoryResponse {

    private UUID orderId;
    private UUID restaurantId;
    private String deliveryAddress;
    private LocalDateTime orderDate;
}
