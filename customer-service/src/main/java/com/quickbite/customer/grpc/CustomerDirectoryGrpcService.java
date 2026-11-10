package com.quickbite.customer.grpc;

import com.quickbite.grpc.customer.v1.CustomerDirectoryGrpc;
import com.quickbite.grpc.customer.v1.ResolveCustomerRequest;
import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** gRPC transport adapter; Spring gRPC registers every {@code BindableService} bean on the server. */
@Service
@RequiredArgsConstructor
public class CustomerDirectoryGrpcService extends CustomerDirectoryGrpc.CustomerDirectoryImplBase {

    private final CustomerLookupService lookups;

    @Override
    public void resolveCustomer(ResolveCustomerRequest request, StreamObserver<ResolveCustomerResponse> response) {
        try {
            response.onNext(lookups.resolve(request));
            response.onCompleted();
        } catch (StatusRuntimeException e) {
            // Report the domain status; an exception thrown out of a handler becomes UNKNOWN.
            response.onError(e);
        }
    }
}
