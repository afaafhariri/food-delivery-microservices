package com.quickbite.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Routes to the four services. Every route has a circuit breaker named after its service
 * ({@link CircuitBreakerConfig}): when a service stops answering (connection refused,
 * timeouts), the breaker opens and the gateway answers 503 from
 * {@link com.quickbite.gateway.web.FallbackController} immediately instead of piling
 * requests onto a service that is down.
 */
@Configuration
public class RouteConfig {

    @Value("${quickbite.services.restaurant-url:http://restaurant-service:8081}")
    private String restaurantServiceUrl;

    @Value("${quickbite.services.order-url:http://order-service:8082}")
    private String orderServiceUrl;

    @Value("${quickbite.services.delivery-url:http://delivery-service:8083}")
    private String deliveryServiceUrl;

    @Value("${quickbite.services.customer-url:http://customer-service:8084}")
    private String customerServiceUrl;

    @Bean
    public RouteLocator quickbiteRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("restaurant-service", r -> r
                        .path("/api/restaurants/**")
                        .filters(breaker("restaurant-service"))
                        .uri(restaurantServiceUrl))
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(breaker("order-service"))
                        .uri(orderServiceUrl))
                .route("delivery-service-drivers", r -> r
                        .path("/api/drivers/**")
                        .filters(breaker("delivery-service"))
                        .uri(deliveryServiceUrl))
                .route("delivery-service-deliveries", r -> r
                        .path("/api/deliveries/**")
                        .filters(breaker("delivery-service"))
                        .uri(deliveryServiceUrl))
                .route("customer-service", r -> r
                        .path("/api/customers/**")
                        .filters(breaker("customer-service"))
                        .uri(customerServiceUrl))
                .build();
    }

    /** Both delivery routes share one breaker: they are the same service. */
    private static Function<GatewayFilterSpec, UriSpec> breaker(String service) {
        return f -> f.circuitBreaker(c -> c
                .setName(service)
                .setFallbackUri("forward:/fallback/" + service));
    }
}
