package com.quickbite.order.service;

import com.quickbite.order.domain.Order;

/**
 * Spring application event: an order moved to CANCELLED. Published inside the transaction
 * that changes the status, and turned into an {@code order.cancelled} outbox event.
 */
public record OrderCancelled(Order order, String reason) {
}
