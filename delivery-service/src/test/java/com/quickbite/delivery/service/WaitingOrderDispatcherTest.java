package com.quickbite.delivery.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link WaitingOrderDispatcher}: how often it asks for an assignment, and that it never throws. */
class WaitingOrderDispatcherTest {

    private final DeliveryService deliveries = mock(DeliveryService.class);
    private final WaitingOrderDispatcher dispatcher = new WaitingOrderDispatcher(deliveries);

    @Test
    void keepsAssigningUntilNoDriverOrOrderIsLeft() {
        when(deliveries.assignOldestWaiting()).thenReturn(true, true, false);

        assertThat(dispatcher.dispatch("test")).isEqualTo(2);
        verify(deliveries, times(3)).assignOldestWaiting();
    }

    @Test
    void stopsAfterTheLimitAndLeavesTheRestToTheSweep() {
        when(deliveries.assignOldestWaiting()).thenReturn(true);

        assertThat(dispatcher.dispatch("test")).isEqualTo(WaitingOrderDispatcher.MAX_PER_RUN);
        verify(deliveries, times(WaitingOrderDispatcher.MAX_PER_RUN)).assignOldestWaiting();
    }

    @Test
    void aFailureIsLoggedNotThrown() {
        // The change that triggered the retry has already committed; failing it now would only
        // turn a successful HTTP request or Kafka record into an error.
        when(deliveries.assignOldestWaiting()).thenReturn(true).thenThrow(new IllegalStateException("db down"));

        assertThat(dispatcher.dispatch("test")).isEqualTo(1);
    }

    @Test
    void theSweepAndTheAfterCommitTriggerBothDispatch() {
        when(deliveries.assignOldestWaiting()).thenReturn(false);

        dispatcher.sweep();
        dispatcher.onRetry(new RetryWaitingOrders("driver freed"));

        verify(deliveries, times(2)).assignOldestWaiting();
    }
}
