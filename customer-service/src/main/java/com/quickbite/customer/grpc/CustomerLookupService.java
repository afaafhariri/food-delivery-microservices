package com.quickbite.customer.grpc;

import com.quickbite.customer.model.Customer;
import com.quickbite.customer.model.CustomerAddress;
import com.quickbite.customer.repository.CustomerAddressRepository;
import com.quickbite.customer.repository.CustomerRepository;
import com.quickbite.grpc.customer.v1.ResolveCustomerRequest;
import com.quickbite.grpc.customer.v1.ResolveCustomerResponse;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerLookupService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    @Transactional(readOnly = true)
    public ResolveCustomerResponse resolve(ResolveCustomerRequest request) {
        UUID customerId = parseId(request.getCustomerId(), "customer_id");
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("customer not found: " + customerId).asRuntimeException());
        if (!customer.isActive()) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("customer " + customerId + " is deactivated").asRuntimeException();
        }

        ResolveCustomerResponse.Builder response = ResolveCustomerResponse.newBuilder()
                .setCustomerId(customerId.toString())
                .setName(customer.getName())
                .setEmail(customer.getEmail());

        if (!request.getAddressId().isEmpty()) {
            UUID addressId = parseId(request.getAddressId(), "address_id");
            CustomerAddress address = addresses.findByIdAndCustomerId(addressId, customerId)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("address " + addressId + " not found for customer " + customerId)
                            .asRuntimeException());
            response.setDeliveryAddress(format(address));
        }
        return response.build();
    }

    private static String format(CustomerAddress a) {
        String postal = a.getPostalCode() == null || a.getPostalCode().isBlank() ? "" : " " + a.getPostalCode();
        return a.getAddressLine() + ", " + a.getCity() + postal;
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw Status.INVALID_ARGUMENT.withDescription("invalid " + field + ": '" + value + "'")
                    .asRuntimeException();
        }
    }
}
