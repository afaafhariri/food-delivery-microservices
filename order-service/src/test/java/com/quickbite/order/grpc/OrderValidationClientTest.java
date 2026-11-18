package com.quickbite.order.grpc;

import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc;
import com.quickbite.grpc.customer.v1.ResolveCustomerRequest;
import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc;
import com.quickbite.order.config.CircuitBreakerConfiguration;
import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.dto.OrderDtos.OrderItemRequest;
import com.quickbite.order.web.ApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Circuit-breaker behaviour of {@link OrderValidationClient} against in-process gRPC servers. */
class OrderValidationClientTest {

    private final AtomicInteger restaurantCalls = new AtomicInteger();
    private final AtomicReference<Status> restaurantAnswer = new AtomicReference<>(Status.OK);

    private Server server;
    private ManagedChannel channel;
    private CircuitBreakerRegistry breakers;
    private OrderValidationClient client;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new RestaurantCatalogGrpc.RestaurantCatalogImplBase() {
                    @Override
                    public void quoteOrder(QuoteOrderRequest request, StreamObserver<QuoteOrderResponse> out) {
                        restaurantCalls.incrementAndGet();
                        Status status = restaurantAnswer.get();
                        if (status.isOk()) {
                            out.onNext(QuoteOrderResponse.newBuilder().setTotalMinor(1_000).build());
                            out.onCompleted();
                        } else {
                            out.onError(status.asRuntimeException());
                        }
                    }
                })
                .addService(new CustomerDirectoryGrpc.CustomerDirectoryImplBase() {
                    @Override
                    public void resolveCustomer(ResolveCustomerRequest request, StreamObserver<ResolveCustomerResponse> out) {
                        out.onNext(ResolveCustomerResponse.newBuilder().setDeliveryAddress("1 Test St").build());
                        out.onCompleted();
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        // Small window so the test needs few calls; a long open state so it stays open.
        breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofHours(1))
                .build());
        client = new OrderValidationClient(
                RestaurantCatalogGrpc.newFutureStub(channel),
                CustomerDirectoryGrpc.newFutureStub(channel),
                Duration.ofSeconds(2),
                breakers);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void validatesWhenBothUpstreamsAnswer() {
        var validated = client.validate(order());
        assertThat(validated.quote().getTotalMinor()).isEqualTo(1_000);
        assertThat(validated.customer().getDeliveryAddress()).isEqualTo("1 Test St");
        assertThat(restaurantBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensAfterRepeatedFailuresAndThenFailsFastWithoutCallingTheUpstream() {
        restaurantAnswer.set(Status.UNAVAILABLE);
        for (int i = 0; i < 4; i++) {
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "restaurant-service unavailable");
        }
        assertThat(restaurantBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(restaurantCalls).hasValue(4);

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "restaurant-service unavailable (circuit open)");
        assertThat(restaurantCalls).as("no RPC while the circuit is open").hasValue(4);
        assertThat(customerBreaker().getMetrics().getNumberOfBufferedCalls())
                .as("customer-service is not charged for restaurant-service's outage").isEqualTo(4);
        assertThat(customerBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void businessErrorsDoNotOpenTheCircuit() {
        restaurantAnswer.set(Status.NOT_FOUND.withDescription("restaurant not found"));
        for (int i = 0; i < 10; i++) {
            assertStatus(HttpStatus.BAD_REQUEST, "restaurant not found");
        }
        assertThat(restaurantBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(restaurantBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void anOpenCustomerCircuitDoesNotLeakRestaurantPermissions() {
        customerBreaker().transitionToForcedOpenState();
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "customer-service unavailable (circuit open)");
        assertThat(restaurantCalls).hasValue(0);
        assertThat(restaurantBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    private void assertStatus(HttpStatus status, String message) {
        assertThatThrownBy(() -> client.validate(order()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    private CircuitBreaker restaurantBreaker() {
        return breakers.circuitBreaker(CircuitBreakerConfiguration.RESTAURANT);
    }

    private CircuitBreaker customerBreaker() {
        return breakers.circuitBreaker(CircuitBreakerConfiguration.CUSTOMER);
    }

    private static CreateOrderRequest order() {
        return new CreateOrderRequest(UUID.randomUUID(), UUID.randomUUID(), null, "1 Test St",
                List.of(new OrderItemRequest(UUID.randomUUID(), 1)));
    }
}
