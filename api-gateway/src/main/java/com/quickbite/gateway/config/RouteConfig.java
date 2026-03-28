package com.quickbite.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                        .uri(restaurantServiceUrl))
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .uri(orderServiceUrl))
                .route("delivery-service-drivers", r -> r
                        .path("/api/drivers/**")
                        .uri(deliveryServiceUrl))
                .route("delivery-service-deliveries", r -> r
                        .path("/api/deliveries/**")
                        .uri(deliveryServiceUrl))
                .route("customer-service", r -> r
                        .path("/api/customers/**")
                        .uri(customerServiceUrl))
                .build();
    }
}
