package com.quickbite.order.grpc;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc.CustomerDirectoryFutureStub;
import com.quickbite.grpc.customer.v1.ResolveCustomerRequest;
import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import com.quickbite.grpc.restaurant.v1.QuoteLine;
import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc.RestaurantCatalogFutureStub;
import com.quickbite.order.config.CircuitBreakerConfiguration;
import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.web.ApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Validates and prices an order by calling restaurant-service and customer-service
 * <b>concurrently</b> over gRPC. Both calls share one deadline budget.
 * <p>
 * Each upstream has a circuit breaker ({@link CircuitBreakerConfiguration}). Only
 * infrastructure failures count against it (UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, ...).
 * Business answers such as NOT_FOUND or FAILED_PRECONDITION mean the upstream is healthy,
 * so they count as successes. While a breaker is open the order is rejected with 503
 * before any RPC is sent.
 */
@Slf4j
@Component
public class OrderValidationClient {

    private final RestaurantCatalogFutureStub catalog;
    private final CustomerDirectoryFutureStub customers;
    private final long deadlineMillis;
    private final CircuitBreaker restaurantBreaker;
    private final CircuitBreaker customerBreaker;

    public OrderValidationClient(RestaurantCatalogFutureStub catalog,
                                 CustomerDirectoryFutureStub customers,
                                 @Value("${quickbite.grpc.deadline:2s}") Duration deadline,
                                 CircuitBreakerRegistry breakers) {
        this.catalog = catalog;
        this.customers = customers;
        this.deadlineMillis = deadline.toMillis();
        this.restaurantBreaker = breakers.circuitBreaker(CircuitBreakerConfiguration.RESTAURANT);
        this.customerBreaker = breakers.circuitBreaker(CircuitBreakerConfiguration.CUSTOMER);
    }

    public record ValidatedOrder(QuoteOrderResponse quote, ResolveCustomerResponse customer) {
    }

    public ValidatedOrder validate(CreateOrderRequest request) {
        QuoteOrderRequest.Builder quote = QuoteOrderRequest.newBuilder()
                .setRestaurantId(request.restaurantId().toString());
        request.items().forEach(item -> quote.addItems(QuoteLine.newBuilder()
                .setMenuItemId(item.menuItemId().toString())
                .setQuantity(item.quantity())));

        ResolveCustomerRequest.Builder customer = ResolveCustomerRequest.newBuilder()
                .setCustomerId(request.customerId().toString());
        if (request.addressId() != null) {
            customer.setAddressId(request.addressId().toString());
        }

        // Both breakers must admit the request before either RPC is sent: an order needs both answers.
        acquire(restaurantBreaker);
        try {
            acquire(customerBreaker);
        } catch (ApiException rejected) {
            restaurantBreaker.releasePermission();
            throw rejected;
        }

        // Fire both RPCs before waiting on either.
        ListenableFuture<QuoteOrderResponse> quoteCall;
        try {
            quoteCall = guarded(restaurantBreaker, () -> catalog
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .quoteOrder(quote.build()));
        } catch (RuntimeException e) {
            customerBreaker.releasePermission();
            throw e;
        }
        ListenableFuture<ResolveCustomerResponse> customerCall = guarded(customerBreaker, () -> customers
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .resolveCustomer(customer.build()));

        try {
            ResolveCustomerResponse resolved = await(customerCall, CircuitBreakerConfiguration.CUSTOMER);
            QuoteOrderResponse priced = await(quoteCall, CircuitBreakerConfiguration.RESTAURANT);
            return new ValidatedOrder(priced, resolved);
        } finally {
            quoteCall.cancel(true);     // no-op when already complete
            customerCall.cancel(true);
        }
    }

    private static void acquire(CircuitBreaker breaker) {
        if (!breaker.tryAcquirePermission()) {
            log.warn("{} circuit is {}, rejecting order without calling it", breaker.getName(), breaker.getState());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, breaker.getName() + " unavailable (circuit open)");
        }
    }

    /**
     * Starts the call and reports its outcome to the breaker when it completes, whichever
     * order the caller awaits the calls in. A call we cancel ourselves (because the other
     * call already failed) says nothing about the upstream, so its permission is released.
     */
    private static <T> ListenableFuture<T> guarded(CircuitBreaker breaker, Supplier<ListenableFuture<T>> rpc) {
        long start = breaker.getCurrentTimestamp();
        ListenableFuture<T> call;
        try {
            call = rpc.get();
        } catch (RuntimeException e) {
            breaker.onError(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit(), e);
            throw e;
        }
        Futures.addCallback(call, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                breaker.onSuccess(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit());
            }

            @Override
            public void onFailure(Throwable error) {
                long elapsed = breaker.getCurrentTimestamp() - start;
                if (call.isCancelled()) {
                    breaker.releasePermission();
                } else if (isBusinessError(Status.fromThrowable(error))) {
                    breaker.onSuccess(elapsed, breaker.getTimestampUnit());
                } else {
                    breaker.onError(elapsed, breaker.getTimestampUnit(), error);
                }
            }
        }, MoreExecutors.directExecutor());
        return call;
    }

    /** The upstream answered: the request itself was wrong. */
    private static boolean isBusinessError(Status status) {
        return switch (status.getCode()) {
            case INVALID_ARGUMENT, NOT_FOUND, FAILED_PRECONDITION -> true;
            default -> false;
        };
    }

    private static <T> T await(ListenableFuture<T> call, String upstream) {
        try {
            return call.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, upstream + " call interrupted");
        } catch (ExecutionException e) {
            throw translate(Status.fromThrowable(e.getCause()), upstream);
        }
    }

    private static ApiException translate(Status status, String upstream) {
        if (isBusinessError(status)) {
            return ApiException.badRequest(status.getDescription());
        }
        log.warn("{} call failed: {} {}", upstream, status.getCode(), status.getDescription());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, upstream + " unavailable");
    }
}
