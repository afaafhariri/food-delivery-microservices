package com.quickbite.order.service;

import java.time.LocalDate;
import java.util.UUID;

public record OrderQuery(UUID customerId, String status, LocalDate startDate, LocalDate endDate) {
}
