package com.quickbite.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Idempotent consumers. Call {@link #firstDelivery} at the start of a {@code @Transactional}
 * listener: it records the event id in {@code processed_events} in the same transaction
 * as the listener's own changes, and returns {@code false} for an event that was already
 * processed, which the listener then ignores.
 * <p>
 * If the listener fails, the transaction rolls back together with the inbox row, so the
 * retried delivery is processed normally.
 */
public class Inbox {

    private static final Logger log = LoggerFactory.getLogger(Inbox.class);

    private static final String INSERT = """
            INSERT INTO processed_events (consumer, event_id) VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcClient jdbc;
    private final MeterRegistry meters;

    public Inbox(JdbcClient jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
    }

    /**
     * @param consumer name of the consuming handler, e.g. its consumer group
     * @param eventId  the {@value EventHeaders#EVENT_ID} header ({@link EventHeaders#eventId})
     * @return {@code true} to process the event, {@code false} if it is a duplicate
     */
    public boolean firstDelivery(String consumer, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true; // published without the outbox: nothing to deduplicate on
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Inbox.firstDelivery must run inside the listener's transaction");
        }
        if (jdbc.sql(INSERT).params(consumer, eventId).update() == 1) {
            return true;
        }
        Counter.builder("quickbite.inbox.duplicates")
                .description("Redelivered events skipped by the inbox")
                .tag("consumer", consumer)
                .register(meters)
                .increment();
        log.info("skipping duplicate event {} for {}", eventId, consumer);
        return false;
    }
}
