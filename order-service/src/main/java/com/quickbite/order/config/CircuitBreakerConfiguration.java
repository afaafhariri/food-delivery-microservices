package com.quickbite.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers for the gRPC upstreams (restaurant-service, customer-service).
 * <p>
 * When an upstream keeps failing (unavailable, deadline exceeded, internal error), its
 * breaker opens and order placement fails fast with 503 instead of waiting out the
 * deadline on every request. After {@code wait-duration-in-open-state} a few trial calls
 * are let through; if they succeed the breaker closes again.
 * <p>
 * State and call counts are exported to Prometheus as {@code resilience4j_circuitbreaker_*}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerConfiguration.Settings.class)
public class CircuitBreakerConfiguration {

    public static final String RESTAURANT = "restaurant-service";
    public static final String CUSTOMER = "customer-service";

    @ConfigurationProperties("quickbite.grpc.circuit-breaker")
    public record Settings(
            /* Number of recent calls the failure rate is computed over. */
            @DefaultValue("20") int slidingWindowSize,
            /* No decision until this many calls have been recorded. */
            @DefaultValue("10") int minimumNumberOfCalls,
            /* Open when at least this percentage of calls failed. */
            @DefaultValue("50") float failureRateThreshold,
            /* How long an open breaker rejects calls before probing again. */
            @DefaultValue("10s") Duration waitDurationInOpenState,
            /* Trial calls allowed while half-open. */
            @DefaultValue("3") int permittedCallsInHalfOpenState) {
    }

    @Bean
    CircuitBreakerRegistry grpcCircuitBreakers(Settings settings, MeterRegistry meters) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker(RESTAURANT);
        registry.circuitBreaker(CUSTOMER);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }
}
