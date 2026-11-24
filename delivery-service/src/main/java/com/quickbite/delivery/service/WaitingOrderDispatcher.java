package com.quickbite.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives waiting orders a driver (see {@link DeliveryService#assignOldestWaiting()}).
 * <p>
 * It runs after every transaction that frees a driver or puts an order on the waiting list,
 * once that transaction has committed, so the freed driver or the new entry is visible. A
 * periodic sweep catches what those triggers miss: a driver freed by hand in the database,
 * an instance that crashed between commit and retry, or a failed attempt. Every instance
 * sweeps; the row locks keep them from taking the same order or driver.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingOrderDispatcher {

    /** Upper bound per run, so one call never holds its thread for long; the sweep does the rest. */
    static final int MAX_PER_RUN = 100;

    private final DeliveryService deliveries;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRetry(RetryWaitingOrders event) {
        dispatch(event.reason());
    }

    @Scheduled(fixedDelayString = "${quickbite.delivery.waiting-sweep-interval:30s}",
            initialDelayString = "${quickbite.delivery.waiting-sweep-interval:30s}")
    public void sweep() {
        dispatch("periodic sweep");
    }

    /**
     * Assigns waiting orders until none is left, no driver is free, or {@link #MAX_PER_RUN} is
     * reached. Never throws: the change that triggered it has already committed, and whatever
     * is left is picked up by the next trigger or sweep.
     *
     * @return how many waiting-list entries were handled (assigned or dropped)
     */
    int dispatch(String reason) {
        int handled = 0;
        try {
            while (handled < MAX_PER_RUN && deliveries.assignOldestWaiting()) {
                handled++;
            }
        } catch (RuntimeException e) {
            log.warn("assigning waiting orders failed after {} ({}); the next sweep retries", reason, e.toString());
        }
        if (handled > 0) {
            log.info("handled {} waiting order(s) after {}", handled, reason);
        }
        return handled;
    }
}
