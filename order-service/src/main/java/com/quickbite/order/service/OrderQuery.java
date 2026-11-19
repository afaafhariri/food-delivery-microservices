package com.quickbite.order.service;

import java.time.LocalDate;
import java.util.UUID;

/** List filters. {@code customerUserId} is set by the service, never by the client: customers see only their own orders. */
public record OrderQuery(UUID customerId, String status, LocalDate startDate, LocalDate endDate, String customerUserId) {

    public OrderQuery(UUID customerId, String status, LocalDate startDate, LocalDate endDate) {
        this(customerId, status, startDate, endDate, null);
    }

    OrderQuery forUser(String userId) {
        return new OrderQuery(customerId, status, startDate, endDate, userId);
    }
}
