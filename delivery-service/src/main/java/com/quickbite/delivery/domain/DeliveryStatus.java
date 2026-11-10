package com.quickbite.delivery.domain;

import java.util.Optional;

public enum DeliveryStatus {
    ASSIGNED, PICKED_UP, EN_ROUTE, DELIVERED;

    public static Optional<DeliveryStatus> parse(String value) {
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
