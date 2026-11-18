package com.quickbite.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Defaults for every route's circuit breaker.
 * <p>
 * Only transport failures count (connection refused, the 15 s response timeout). An HTTP
 * error answered by a service, such as a 400 or the order-service's own 503, means the
 * service is up, so it is passed through unchanged and does not trip the breaker.
 * <p>
 * The time limiter is set above the HTTP client's response timeout: the client gives up
 * first and its timeout is what the breaker records. (Resilience4j's default of 1 s would
 * cut off slow but healthy requests.)
 */
@Configuration(proxyBeanMethods = false)
public class CircuitBreakerConfig {

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> routeCircuitBreakerDefaults(
            @Value("${spring.cloud.gateway.server.webflux.httpclient.response-timeout:15s}") Duration responseTimeout) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(responseTimeout.plusSeconds(1))
                        .build())
                .build());
    }
}
