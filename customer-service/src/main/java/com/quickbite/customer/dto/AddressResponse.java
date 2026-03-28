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
public class AddressResponse {

    private UUID id;
    private UUID customerId;
    private String label;
    private String addressLine;
    private String city;
    private String postalCode;
    private LocalDateTime createdAt;
}
