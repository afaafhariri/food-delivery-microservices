package com.quickbite.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Outbox, relay and inbox against real Postgres and Kafka. Skipped when Docker is not available. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withStartupAttempts(3);

    /*
     * Up to 3 start attempts: Testcontainers copies a start script into the Kafka container and
     * runs it at once, and now and then the kernel still has the file open for writing
     * ("Text file busy", exit 126). A fresh container avoids the race; this is not a test failure.
     */
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.2")
            .withStartupAttempts(3);

    @Autowired
    Outbox outbox;

    @Autowired
    Inbox inbox;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void committedEventsReachKafkaInOrderWithTheirEventId() {
        String topic = "test.ordered." + UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            int n = i;
            ids.add(tx.execute(s -> outbox.publish(topic, "order-1", Map.of("seq", n))));
        }

        List<ConsumerRecord<String, String>> records = consume(topic, 25);

        // Payloads are stored as jsonb, which normalises whitespace: {"seq":0} comes back as {"seq": 0}.
        assertThat(records).extracting(ConsumerRecord::value)
                .containsExactlyElementsOf(IntStream.range(0, 25).mapToObj(i -> "{\"seq\": " + i + "}").toList());
        assertThat(records).extracting(EventHeaders::eventId).containsExactlyElementsOf(ids.stream().map(UUID::toString).toList());
        assertThat(records).extracting(ConsumerRecord::key).containsOnly("order-1");
        awaitPublished(ids.getLast());
    }

    @Test
    void rolledBackEventsAreNeverPublished() {
        String topic = "test.rollback." + UUID.randomUUID();
        UUID[] rolledBack = new UUID[1];
        tx.executeWithoutResult(s -> {
            rolledBack[0] = outbox.publish(topic, "k", Map.of("state", "rolled-back"));
            s.setRollbackOnly();
        });
        UUID committed = tx.execute(s -> outbox.publish(topic, "k", Map.of("state", "committed")));

        List<ConsumerRecord<String, String>> records = consume(topic, 1);
        assertThat(records).extracting(ConsumerRecord::value).containsExactly("{\"state\": \"committed\"}");
        assertThat(rowCount(rolledBack[0])).isZero();
        awaitPublished(committed);
    }

    @Test
    void kafkaOutageDelaysButNeverLosesEvents() throws Exception {
        String topic = "test.outage." + UUID.randomUUID();
        kafkaTemplate.send(topic, "warm-up").get(); // topic exists, producer metadata cached

        var docker = kafka.getDockerClient();
        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        List<UUID> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                int n = i;
                ids.add(tx.execute(s -> outbox.publish(topic, "k", Map.of("seq", n))));
            }
            sleep(6_000); // the relay's sends time out and are retried
            assertThat(ids).allSatisfy(id -> assertThat(isPublished(id)).as("not published while Kafka is down").isFalse());
            assertThat(jdbc.sql("SELECT max(attempts) FROM outbox_events WHERE event_id IN (:ids)")
                    .param("ids", ids).query(Integer.class).single()).isPositive();
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        List<ConsumerRecord<String, String>> records = consume(topic, 4);
        assertThat(records).extracting(EventHeaders::eventId).containsSubsequence(
                ids.stream().map(UUID::toString).toList());
        ids.forEach(this::awaitPublished);
    }

    @Test
    void publishOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> outbox.publish("t", "k", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inboxReportsEachEventOnlyOncePerConsumer() {
        String eventId = UUID.randomUUID().toString();
        assertThat(firstDelivery("consumer-a", eventId)).isTrue();
        assertThat(firstDelivery("consumer-a", eventId)).isFalse();
        assertThat(firstDelivery("consumer-b", eventId)).as("other consumers are independent").isTrue();
        assertThat(firstDelivery("consumer-a", null)).as("no id: cannot deduplicate").isTrue();
    }

    @Test
    void inboxRowRollsBackWithTheListener() {
        String eventId = UUID.randomUUID().toString();
        tx.executeWithoutResult(s -> {
            inbox.firstDelivery("consumer-a", eventId);
            s.setRollbackOnly(); // the listener failed
        });
        assertThat(firstDelivery("consumer-a", eventId))
                .as("the retried delivery is processed").isTrue();
    }

    private boolean firstDelivery(String consumer, String eventId) {
        return Boolean.TRUE.equals(tx.execute(s -> inbox.firstDelivery(consumer, eventId)));
    }

    private int rowCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE event_id = ?").param(eventId).query(Integer.class).single();
    }

    private boolean isPublished(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT published_at IS NOT NULL FROM outbox_events WHERE event_id = ?")
                .param(eventId).query(Boolean.class).single());
    }

    private void awaitPublished(UUID eventId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (isPublished(eventId)) {
                return;
            }
            sleep(100);
        }
        throw new AssertionError("event " + eventId + " was not marked published");
    }

    private static List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (records.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(records::add);
            }
            // A little longer, to catch anything that should not be there.
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
