package com.quickbite.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends committed outbox events to Kafka.
 * <p>
 * One background thread per service instance. Each pass runs in a database transaction that
 * first takes a Postgres advisory lock, so with several replicas only one relays at a time
 * and events leave in {@code id} order. A pass sends up to {@code batch-size} events,
 * waits for Kafka's acknowledgements and marks the acknowledged ones published.
 * <p>
 * If a send fails, only the events before it are marked published; it and everything after
 * it are sent again on the next pass. Kafka may then see a later event twice, but the last
 * copy of each event always arrives after the events before it, so consumers end in the
 * right state. Failed passes back off exponentially up to {@code max-backoff}.
 * <p>
 * The trace context stored with each event is restored, so the Kafka producer and consumer
 * spans join the trace of the request that wrote the event.
 */
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Any constant works: each service has its own database. */
    private static final long RELAY_LOCK = 0x5142_4f55_5442_4f58L; // "QBOUTBOX"

    private static final Duration HOUSEKEEPING_INTERVAL = Duration.ofHours(1);
    private static final Duration STATS_TTL = Duration.ofSeconds(5);
    private static final TypeReference<Map<String, String>> HEADER_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final MessagingProperties properties;
    private final ObservationRegistry observations;
    private final Counter published;
    private final Counter failures;

    private final Semaphore wake = new Semaphore(0);
    private volatile boolean running;
    private Thread worker;
    private Instant nextHousekeeping = Instant.EPOCH;

    private volatile Stats stats = new Stats(0, 0, Instant.EPOCH);

    record Stats(double pending, double oldestAgeSeconds, Instant takenAt) {
    }

    record PendingEvent(long id, UUID eventId, String topic, String key, String payload, Map<String, String> headers) {
    }

    record Pass(int sent, Throwable failure) {
        static final Pass NOTHING = new Pass(0, null);
    }

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, ObjectMapper json,
                       MessagingProperties properties, ObservationRegistry observations, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.properties = properties;
        this.observations = observations;
        this.published = Counter.builder("quickbite.outbox.published")
                .description("Outbox events acknowledged by Kafka").register(meters);
        this.failures = Counter.builder("quickbite.outbox.failures")
                .description("Failed outbox sends (they are retried)").register(meters);
        Gauge.builder("quickbite.outbox.pending", this, r -> r.stats().pending())
                .description("Committed events not yet acknowledged by Kafka").register(meters);
        Gauge.builder("quickbite.outbox.oldest.age", this, r -> r.stats().oldestAgeSeconds())
                .description("Age of the oldest unpublished event").baseUnit("seconds").register(meters);
    }

    /** Called after a transaction that wrote an event commits. */
    public void wakeUp() {
        wake.release();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("outbox-relay").daemon().start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.outbox().relayEnabled();
    }

    // ── Relay loop ───────────────────────────────────────────────────────────

    private void loop() {
        MessagingProperties.Outbox config = properties.outbox();
        Duration backoff = config.pollInterval();
        log.info("outbox relay started (batch {}, poll {})", config.batchSize(), config.pollInterval());
        while (running) {
            try {
                Pass pass = relayOnce();
                if (pass.failure() != null) {
                    log.warn("outbox relay: Kafka send failed, retrying in {}: {}", backoff, describe(pass.failure()));
                    Thread.sleep(backoff);
                    backoff = min(backoff.multipliedBy(2), config.maxBackoff());
                    continue;
                }
                backoff = config.pollInterval();
                housekeepIfDue();
                if (pass.sent() < config.batchSize()) {
                    // Caught up: sleep until a commit wakes us or the poll interval passes.
                    wake.tryAcquire(config.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
                    wake.drainPermits();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                log.warn("outbox relay pass failed, retrying in {}: {}", backoff, e.toString());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoff = min(backoff.multipliedBy(2), config.maxBackoff());
            }
        }
        log.info("outbox relay stopped");
    }

    /** One relay pass. Package-private for tests. */
    Pass relayOnce() {
        Pass pass = tx.execute(status -> {
            boolean leader = Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(?)")
                    .param(RELAY_LOCK).query(Boolean.class).single());
            if (!leader) {
                return Pass.NOTHING; // another replica is relaying
            }
            List<PendingEvent> events = jdbc.sql("""
                            SELECT id, event_id, topic, message_key, payload::text AS payload, headers::text AS headers
                            FROM outbox_events
                            WHERE published_at IS NULL
                            ORDER BY id
                            LIMIT ?
                            """)
                    .param(properties.outbox().batchSize())
                    .query(this::toEvent)
                    .list();
            if (events.isEmpty()) {
                return Pass.NOTHING;
            }

            // Send the whole batch, then wait for the acknowledgements in order.
            List<CompletableFuture<?>> sends = new ArrayList<>(events.size());
            for (PendingEvent event : events) {
                sends.add(send(event));
            }
            long deadline = System.nanoTime() + properties.outbox().sendTimeout().toNanos();
            List<Long> acknowledged = new ArrayList<>(events.size());
            Throwable failure = null;
            for (int i = 0; i < events.size(); i++) {
                try {
                    sends.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    acknowledged.add(events.get(i).id());
                } catch (ExecutionException e) {
                    failure = e.getCause();
                } catch (TimeoutException e) {
                    failure = e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = e;
                }
                if (failure != null) {
                    recordFailure(events.get(i), failure);
                    break;
                }
            }
            if (!acknowledged.isEmpty()) {
                jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id IN (:ids)")
                        .param("ids", acknowledged)
                        .update();
            }
            return new Pass(acknowledged.size(), failure);
        });
        published.increment(pass.sent());
        return pass;
    }

    private CompletableFuture<?> send(PendingEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.key(), event.payload());
        record.headers().add(EventHeaders.EVENT_ID, event.eventId().toString().getBytes(StandardCharsets.UTF_8));

        // Continue the trace of the request that wrote the event: this observation's parent is
        // extracted from the stored headers, and KafkaTemplate's producer span becomes its child.
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(event.headers());
        context.setRemoteServiceName("outbox");
        try {
            return Observation.createNotStarted("quickbite.outbox.relay", () -> context, observations)
                    .contextualName("outbox relay " + event.topic())
                    .lowCardinalityKeyValue("topic", event.topic())
                    .observe(() -> kafka.send(record));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e); // e.g. metadata unavailable within max.block.ms
        }
    }

    private void recordFailure(PendingEvent event, Throwable failure) {
        failures.increment();
        jdbc.sql("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?")
                .params(truncate(describe(failure), 2000), event.id())
                .update();
    }

    private PendingEvent toEvent(ResultSet rs, int row) throws SQLException {
        return new PendingEvent(rs.getLong("id"), rs.getObject("event_id", UUID.class), rs.getString("topic"),
                rs.getString("message_key"), rs.getString("payload"),
                json.readValue(rs.getString("headers"), HEADER_MAP));
    }

    // ── Housekeeping and metrics ─────────────────────────────────────────────

    private void housekeepIfDue() {
        Instant now = Instant.now();
        if (now.isBefore(nextHousekeeping)) {
            return;
        }
        nextHousekeeping = now.plus(HOUSEKEEPING_INTERVAL);
        int events = jdbc.sql("DELETE FROM outbox_events WHERE published_at < now() - make_interval(secs => ?)")
                .param((double) properties.outbox().retention().toSeconds()).update();
        int ids = jdbc.sql("DELETE FROM processed_events WHERE processed_at < now() - make_interval(secs => ?)")
                .param((double) properties.inbox().retention().toSeconds()).update();
        if (events + ids > 0) {
            log.info("outbox housekeeping: removed {} published events and {} processed event ids", events, ids);
        }
    }

    /** Backlog for the gauges, re-read at most every few seconds (Prometheus scrapes every 15 s). */
    private Stats stats() {
        Stats current = stats;
        if (Duration.between(current.takenAt(), Instant.now()).compareTo(STATS_TTL) < 0) {
            return current;
        }
        try {
            current = jdbc.sql("""
                            SELECT count(*) AS pending,
                                   COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0) AS age
                            FROM outbox_events WHERE published_at IS NULL
                            """)
                    .query((rs, row) -> new Stats(rs.getLong("pending"), rs.getDouble("age"), Instant.now()))
                    .single();
        } catch (RuntimeException e) {
            current = new Stats(Double.NaN, Double.NaN, Instant.now()); // database unreachable
        }
        stats = current;
        return current;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The exception and its causes on one line ("Failed to construct kafka producer" alone says nothing). */
    private static String describe(Throwable failure) {
        StringBuilder text = new StringBuilder(failure.toString());
        for (Throwable cause = failure.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            text.append(" <- ").append(cause);
        }
        return text.toString();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
