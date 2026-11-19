package com.quickbite.order.domain;

import org.junit.jupiter.api.Test;

import static com.quickbite.order.domain.OrderStatus.CANCELLED;
import static com.quickbite.order.domain.OrderStatus.CONFIRMED;
import static com.quickbite.order.domain.OrderStatus.DELIVERED;
import static com.quickbite.order.domain.OrderStatus.PICKED_UP;
import static com.quickbite.order.domain.OrderStatus.PLACED;
import static com.quickbite.order.domain.OrderStatus.PREPARING;
import static com.quickbite.order.domain.OrderStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void theHappyPathIsAllowed() {
        assertThat(PLACED.canMoveTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canMoveTo(PREPARING)).isTrue();
        assertThat(PREPARING.canMoveTo(READY)).isTrue();
        assertThat(READY.canMoveTo(PICKED_UP)).isTrue();
        assertThat(PICKED_UP.canMoveTo(DELIVERED)).isTrue();
    }

    @Test
    void finishedOrdersNeverChangeAgain() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(DELIVERED.canMoveTo(next)).as("DELIVERED -> " + next).isEqualTo(next == DELIVERED);
            assertThat(CANCELLED.canMoveTo(next)).as("CANCELLED -> " + next).isEqualTo(next == CANCELLED);
        }
    }

    @Test
    void noGoingBackwardsOrSkippingThePickup() {
        assertThat(CONFIRMED.canMoveTo(PLACED)).isFalse();
        assertThat(PICKED_UP.canMoveTo(READY)).isFalse();
        assertThat(PLACED.canMoveTo(DELIVERED)).isFalse();
        assertThat(PICKED_UP.canMoveTo(CANCELLED)).as("food on its way").isFalse();
    }

    @Test
    void sameStatusIsAHarmlessNoOp() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canMoveTo(status)).isTrue();
        }
    }

    @Test
    void allowedBeforeListsTheValidStartingPoints() {
        assertThat(OrderStatus.allowedBefore(DELIVERED)).containsExactlyInAnyOrder(PICKED_UP, DELIVERED);
        assertThat(OrderStatus.allowedBefore(PICKED_UP))
                .containsExactlyInAnyOrder(PLACED, CONFIRMED, PREPARING, READY, PICKED_UP);
    }
}
