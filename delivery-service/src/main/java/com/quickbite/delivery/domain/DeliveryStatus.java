package com.quickbite.delivery.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A delivery's lifecycle, and which changes are allowed. Moving to the current status is allowed
 * (a no-op).
 *
 * <pre>
 * ASSIGNED ──► PICKED_UP ──► EN_ROUTE ──► DELIVERED
 *    │              └───────────────────────▲
 *    └──► CANCELLED
 * </pre>
 * Only an ASSIGNED delivery can be cancelled; once the food is picked up it must be delivered.
 * DELIVERED and CANCELLED are final.
 */
public enum DeliveryStatus {
    ASSIGNED, PICKED_UP, EN_ROUTE, DELIVERED, CANCELLED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> NEXT = Map.of(
            ASSIGNED, EnumSet.of(PICKED_UP, CANCELLED),
            PICKED_UP, EnumSet.of(EN_ROUTE, DELIVERED),
            EN_ROUTE, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(DeliveryStatus.class),
            CANCELLED, EnumSet.noneOf(DeliveryStatus.class));

    public boolean canMoveTo(DeliveryStatus next) {
        return next == this || NEXT.get(this).contains(next);
    }

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
