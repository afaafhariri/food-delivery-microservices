package com.quickbite.order.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc.CustomerDirectoryFutureStub;
import com.quickbite.grpc.customer.v1.ResolveCustomerRequest;
import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import com.quickbite.grpc.restaurant.v1.QuoteLine;
import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc.RestaurantCatalogFutureStub;
import com.quickbite.order.dto.OrderDtos.CreateOrderRequest;
import com.quickbite.order.web.ApiException;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Validates and prices an order by calling restaurant-service and customer-service
 * <b>concurrently</b> over gRPC. Both calls share one deadline budget.
 */
@Slf4j
@Component
public class OrderValidationClient {

    private final RestaurantCatalogFutureStub catalog;
    private final CustomerDirectoryFutureStub customers;
    private final long deadlineMillis;

    public OrderValidationClient(RestaurantCatalogFutureStub catalog,
                                 CustomerDirectoryFutureStub customers,
                                 @Value("${quickbite.grpc.deadline:2s}") Duration deadline) {
        this.catalog = catalog;
        this.customers = customers;
        this.deadlineMillis = deadline.toMillis();
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

        // Fire both RPCs before waiting on either.
        ListenableFuture<QuoteOrderResponse> quoteCall = catalog
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .quoteOrder(quote.build());
        ListenableFuture<ResolveCustomerResponse> customerCall = customers
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .resolveCustomer(customer.build());

        try {
            ResolveCustomerResponse resolved = await(customerCall, "customer-service");
            QuoteOrderResponse priced = await(quoteCall, "restaurant-service");
            return new ValidatedOrder(priced, resolved);
        } finally {
            quoteCall.cancel(true);     // no-op when already complete
            customerCall.cancel(true);
        }
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
        return switch (status.getCode()) {
            case INVALID_ARGUMENT, NOT_FOUND, FAILED_PRECONDITION ->
                    ApiException.badRequest(status.getDescription());
            default -> {
                log.warn("{} call failed: {} {}", upstream, status.getCode(), status.getDescription());
                yield new ApiException(HttpStatus.SERVICE_UNAVAILABLE, upstream + " unavailable");
            }
        };
    }
}
