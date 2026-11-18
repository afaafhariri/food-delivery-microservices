package com.quickbite.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Transactional outbox: the only way services publish events.
 * <p>
 * {@link #publish} inserts the event into {@code outbox_events} using the caller's
 * transaction. If that transaction commits, the business change and the event are both
 * stored; if it rolls back, neither is. {@link OutboxRelay} then sends committed events to
 * Kafka, in order, retrying until Kafka acknowledges them. Nothing is lost when Kafka is
 * down or the service crashes between the commit and the send.
 * <p>
 * Delivery is at-least-once: an event can reach Kafka twice (for example if the service
 * dies after Kafka acknowledged it but before the row was marked published). Consumers
 * skip duplicates with {@link Inbox}.
 */
public class Outbox {

    private static final String INSERT = """
            INSERT INTO outbox_events (event_id, topic, message_key, payload, headers)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final TraceHeaders traceHeaders;
    private final OutboxRelay relay;

    public Outbox(JdbcClient jdbc, ObjectMapper json, TraceHeaders traceHeaders, OutboxRelay relay) {
        this.jdbc = jdbc;
        this.json = json;
        this.traceHeaders = traceHeaders;
        this.relay = relay;
    }

    /**
     * Records an event in the current transaction.
     *
     * @param topic   Kafka topic
     * @param key     record key (events with the same key keep their order); may be null
     * @param payload serialized to JSON
     * @return the event id, sent as the {@value EventHeaders#EVENT_ID} header
     * @throws IllegalStateException if no transaction is active
     */
    public UUID publish(String topic, String key, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Outbox.publish must run inside the transaction that makes the business change");
        }
        UUID eventId = UUID.randomUUID();
        jdbc.sql(INSERT)
                .params(eventId, topic, key, json.writeValueAsString(payload),
                        json.writeValueAsString(traceHeaders.current()))
                .update();
        // Low latency: wake the relay as soon as the event is visible to it.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relay.wakeUp();
            }
        });
        return eventId;
    }
}
