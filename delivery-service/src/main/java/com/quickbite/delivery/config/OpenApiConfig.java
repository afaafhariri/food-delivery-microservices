package com.quickbite.delivery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI deliveryServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Delivery Service API")
                .version("2.0")
                .description("API for managing deliveries and drivers in QuickBite"));
    }
}
