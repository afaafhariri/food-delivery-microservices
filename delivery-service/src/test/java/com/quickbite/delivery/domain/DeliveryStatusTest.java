package com.quickbite.delivery.domain;

import org.junit.jupiter.api.Test;

import static com.quickbite.delivery.domain.DeliveryStatus.ASSIGNED;
import static com.quickbite.delivery.domain.DeliveryStatus.CANCELLED;
import static com.quickbite.delivery.domain.DeliveryStatus.DELIVERED;
import static com.quickbite.delivery.domain.DeliveryStatus.EN_ROUTE;
import static com.quickbite.delivery.domain.DeliveryStatus.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @Test
    void forwardOnly() {
        assertThat(ASSIGNED.canMoveTo(PICKED_UP)).isTrue();
        assertThat(PICKED_UP.canMoveTo(EN_ROUTE)).isTrue();
        assertThat(PICKED_UP.canMoveTo(DELIVERED)).isTrue();
        assertThat(EN_ROUTE.canMoveTo(DELIVERED)).isTrue();

        assertThat(ASSIGNED.canMoveTo(DELIVERED)).as("must be picked up first").isFalse();
        assertThat(EN_ROUTE.canMoveTo(PICKED_UP)).isFalse();
        assertThat(PICKED_UP.canMoveTo(CANCELLED)).as("food on its way").isFalse();
    }

    @Test
    void finishedDeliveriesNeverChangeAgain() {
        for (DeliveryStatus next : DeliveryStatus.values()) {
            assertThat(DELIVERED.canMoveTo(next)).isEqualTo(next == DELIVERED);
            assertThat(CANCELLED.canMoveTo(next)).isEqualTo(next == CANCELLED);
        }
    }
}
