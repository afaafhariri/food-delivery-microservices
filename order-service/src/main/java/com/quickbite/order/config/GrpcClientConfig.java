package com.quickbite.order.config;

import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc;
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
 * A service config adds transparent retries for {@code UNAVAILABLE}, e.g. while a peer restarts.
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
        // gRPC's service-config parser requires JSON-like types (numbers as Double).
        Map<String, Object> retryPolicy = Map.of(
                "maxAttempts", 3.0,
                "initialBackoff", "0.05s",
                "maxBackoff", "0.4s",
                "backoffMultiplier", 2.0,
                "retryableStatusCodes", List.of("UNAVAILABLE"));
        Map<String, Object> serviceConfig = Map.of("methodConfig", List.of(Map.of(
                "name", List.of(Map.of("service", serviceName)),
                "retryPolicy", retryPolicy)));
        return ChannelBuilderOptions.defaults()
                .withCustomizer((name, builder) -> builder.enableRetry().defaultServiceConfig(serviceConfig));
    }
}
