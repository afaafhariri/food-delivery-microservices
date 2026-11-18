package com.quickbite.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** {@code quickbite.messaging.*} settings. The defaults suit every QuickBite service. */
@ConfigurationProperties("quickbite.messaging")
public record MessagingProperties(@DefaultValue Outbox outbox, @DefaultValue Inbox inbox, @DefaultValue Consumer consumer) {

    /**
     * @param relayEnabled  run the relay in this process (off only in tests)
     * @param pollInterval  how often the relay looks for events it was not woken up for
     * @param batchSize     events sent per relay transaction
     * @param sendTimeout   how long a batch may wait for Kafka acknowledgements
     * @param maxBackoff    longest pause between attempts while Kafka is unreachable
     * @param retention     how long published events are kept (for debugging and replay)
     */
    public record Outbox(
            @DefaultValue("true") boolean relayEnabled,
            @DefaultValue("1s") Duration pollInterval,
            @DefaultValue("100") int batchSize,
            @DefaultValue("30s") Duration sendTimeout,
            @DefaultValue("30s") Duration maxBackoff,
            @DefaultValue("7d") Duration retention) {
    }

    /** @param retention how long processed event ids are remembered; must exceed any redelivery window */
    public record Inbox(@DefaultValue("14d") Duration retention) {
    }

    /**
     * Retries for a {@code @KafkaListener} that throws: exponential backoff, then the record
     * goes to {@code <topic>.DLT}. {@link NonRetryableEventException} skips the retries.
     */
    public record Consumer(
            @DefaultValue("5") int maxRetries,
            @DefaultValue("500ms") Duration initialBackoff,
            @DefaultValue("10s") Duration maxBackoff) {
    }
}
