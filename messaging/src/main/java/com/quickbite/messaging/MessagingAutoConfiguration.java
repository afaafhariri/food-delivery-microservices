package com.quickbite.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires the outbox, its relay, the inbox and the consumer error handler into every service
 * that has this module, a DataSource and Kafka on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, JdbcClient.class, DataSource.class})
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    OutboxRelay outboxRelay(DataSource dataSource, PlatformTransactionManager transactions,
                            KafkaTemplate<String, String> kafka, ObjectMapper json, MessagingProperties properties,
                            ObjectProvider<ObservationRegistry> observations, ObjectProvider<MeterRegistry> meters) {
        return new OutboxRelay(JdbcClient.create(dataSource), new TransactionTemplate(transactions), kafka, json,
                properties, observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                meters.getIfAvailable(() -> Metrics.globalRegistry));
    }

    @Bean
    Outbox outbox(DataSource dataSource, ObjectMapper json, ObjectProvider<TraceHeaders> traceHeaders,
                  OutboxRelay relay) {
        return new Outbox(JdbcClient.create(dataSource), json, traceHeaders.getIfAvailable(() -> TraceHeaders.NONE),
                relay);
    }

    @Bean
    Inbox inbox(DataSource dataSource, ObjectProvider<MeterRegistry> meters) {
        return new Inbox(JdbcClient.create(dataSource), meters.getIfAvailable(() -> Metrics.globalRegistry));
    }

    /**
     * Listener failures: retry with exponential backoff, then publish the record to
     * {@code <topic>.DLT} (Kafka picks the partition) and move on, so one bad record never
     * blocks a partition. {@link NonRetryableEventException} goes to the DLT at once.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafka, MessagingProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + Topics.DEAD_LETTER_SUFFIX, -1));
        MessagingProperties.Consumer config = properties.consumer();
        var backOff = new ExponentialBackOffWithMaxRetries(config.maxRetries());
        backOff.setInitialInterval(config.initialBackoff().toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(config.maxBackoff().toMillis());
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }

    /**
     * Every QuickBite topic and its dead-letter topic, created by Spring's {@link KafkaAdmin} at
     * startup if missing (the broker no longer auto-creates topics). Existing topics get more
     * partitions or the declared retention if they differ ({@code spring.kafka.admin.modify-topic-configs}).
     */
    @Bean
    @ConditionalOnProperty(prefix = "quickbite.messaging.topics", name = "declare", matchIfMissing = true)
    KafkaAdmin.NewTopics quickbiteTopics(MessagingProperties properties) {
        MessagingProperties.Topics config = properties.topics();
        List<NewTopic> topics = new ArrayList<>();
        for (String name : Topics.ALL) {
            topics.add(TopicBuilder.name(name)
                    .partitions(config.partitions())
                    .replicas(config.replicas())
                    .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.retention().toMillis()))
                    .build());
            topics.add(TopicBuilder.name(name + Topics.DEAD_LETTER_SUFFIX)
                    .partitions(1)                       // a trickle of failed events; order is irrelevant
                    .replicas(config.replicas())
                    .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.deadLetterRetention().toMillis()))
                    .build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    /** Stores the current trace context with each event when Micrometer Tracing is present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    static class TracingConfiguration {

        @Bean
        TraceHeaders outboxTraceHeaders(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            return () -> {
                Tracer t = tracer.getIfAvailable();
                Propagator p = propagator.getIfAvailable();
                Span span = t == null ? null : t.currentSpan();
                if (span == null || p == null) {
                    return Map.of();
                }
                Map<String, String> headers = new LinkedHashMap<>();
                p.inject(span.context(), headers, Map::put);
                return headers;
            };
        }
    }
}
