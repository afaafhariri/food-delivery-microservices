package com.quickbite.order.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An order's lifecycle, and which changes are allowed. Moving to the status an order already has
 * is always allowed (a no-op), so repeated requests and redelivered events are harmless.
 *
 * <pre>
 * PLACED ──► CONFIRMED ──► PREPARING ──► READY ──► PICKED_UP ──► DELIVERED
 *    │           │             │           │
 *    └───────────┴─────────────┴───────────┴──► CANCELLED
 * </pre>
 * A driver may also pick up a PLACED, CONFIRMED or PREPARING order (delivery-service does not wait
 * for the kitchen's status). DELIVERED and CANCELLED are final; a picked-up order can no longer be
 * cancelled: the food is on its way.
 */
public enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, READY, PICKED_UP, DELIVERED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            PLACED, EnumSet.of(CONFIRMED, PICKED_UP, CANCELLED),
            CONFIRMED, EnumSet.of(PREPARING, READY, PICKED_UP, CANCELLED),
            PREPARING, EnumSet.of(READY, PICKED_UP, CANCELLED),
            READY, EnumSet.of(PICKED_UP, CANCELLED),
            PICKED_UP, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canMoveTo(OrderStatus next) {
        return next == this || NEXT.get(this).contains(next);
    }

    /** Every status from which {@code target} may be reached (including {@code target} itself). */
    public static Set<OrderStatus> allowedBefore(OrderStatus target) {
        EnumSet<OrderStatus> from = EnumSet.noneOf(OrderStatus.class);
        Arrays.stream(values()).filter(s -> s.canMoveTo(target)).forEach(from::add);
        return from;
    }

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
