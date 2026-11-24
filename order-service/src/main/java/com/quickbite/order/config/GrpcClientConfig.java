package com.quickbite.order.config;

import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc;
import com.quickbite.order.grpc.BearerTokenForwarder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;
import java.util.Map;

/**
 * gRPC channels to restaurant-service and customer-service.
 * <p>
 * Channels are long-lived HTTP/2 connections (created lazily, closed by Spring on shutdown).
 * The service config ({@link #serviceConfig}) spreads calls over every address the target
 * resolves to ({@code round_robin}; in Kubernetes a headless Service lists every pod) and
 * retries {@code UNAVAILABLE} transparently, e.g. while a peer restarts.
 * {@link BearerTokenForwarder} sends the caller's access token: the gRPC servers require one.
 * Per-call deadlines are applied by {@link com.quickbite.order.grpc.OrderValidationClient}.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcClientConfig {

    @Bean
    RestaurantCatalogGrpc.RestaurantCatalogFutureStub restaurantCatalogStub(GrpcChannelFactory channels) {
        return RestaurantCatalogGrpc.newFutureStub(
                channels.createChannel("restaurant", withRetries(RestaurantCatalogGrpc.SERVICE_NAME)));
    }

    @Bean
    CustomerDirectoryGrpc.CustomerDirectoryFutureStub customerDirectoryStub(GrpcChannelFactory channels) {
        return CustomerDirectoryGrpc.newFutureStub(
                channels.createChannel("customer", withRetries(CustomerDirectoryGrpc.SERVICE_NAME)));
    }

    private static ChannelBuilderOptions withRetries(String serviceName) {
        Map<String, Object> serviceConfig = serviceConfig(serviceName);
        return ChannelBuilderOptions.defaults()
                .withCustomizer((name, builder) -> builder.enableRetry().defaultServiceConfig(serviceConfig)
                        .intercept(new BearerTokenForwarder()));
    }

    /**
     * The gRPC service config of a channel to {@code serviceName}: one connection to every
     * address the target resolves to, calls taken in turn ({@code round_robin}; gRPC's own
     * default, {@code pick_first}, would send them all to one), and up to 3 attempts on
     * {@code UNAVAILABLE}. gRPC's parser requires JSON-like types (numbers as Double).
     */
    static Map<String, Object> serviceConfig(String serviceName) {
        Map<String, Object> retryPolicy = Map.of(
                "maxAttempts", 3.0,
                "initialBackoff", "0.05s",
                "maxBackoff", "0.4s",
                "backoffMultiplier", 2.0,
                "retryableStatusCodes", List.of("UNAVAILABLE"));
        return Map.of(
                "loadBalancingConfig", List.of(Map.of("round_robin", Map.of())),
                "methodConfig", List.of(Map.of(
                        "name", List.of(Map.of("service", serviceName)),
                        "retryPolicy", retryPolicy)));
    }
}
