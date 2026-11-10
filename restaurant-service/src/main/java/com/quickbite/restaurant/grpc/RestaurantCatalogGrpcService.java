package com.quickbite.restaurant.grpc;

import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** gRPC transport adapter; Spring gRPC registers every {@code BindableService} bean on the server. */
@Service
@RequiredArgsConstructor
public class RestaurantCatalogGrpcService extends RestaurantCatalogGrpc.RestaurantCatalogImplBase {

    private final OrderQuoteService quotes;

    @Override
    public void quoteOrder(QuoteOrderRequest request, StreamObserver<QuoteOrderResponse> response) {
        try {
            response.onNext(quotes.quote(request));
            response.onCompleted();
        } catch (StatusRuntimeException e) {
            // Report the domain status; an exception thrown out of a handler becomes UNKNOWN.
            response.onError(e);
        }
    }
}
