package com.quickbite.order.domain;

import java.util.Optional;

public enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, READY, PICKED_UP, DELIVERED, CANCELLED;

    public static Optional<OrderStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
