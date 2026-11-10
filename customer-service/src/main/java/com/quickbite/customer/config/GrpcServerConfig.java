package com.quickbite.customer.config;

import io.grpc.ServerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;

import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class GrpcServerConfig {

    /** gRPC handlers do blocking JPA work: run each call on a virtual thread. */
    @Bean
    <T extends ServerBuilder<T>> ServerBuilderCustomizer<T> grpcVirtualThreadExecutor() {
        return builder -> builder.executor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
