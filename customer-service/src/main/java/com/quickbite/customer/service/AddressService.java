package com.quickbite.customer.service;

import com.quickbite.customer.dto.AddressRequest;
import com.quickbite.customer.dto.AddressResponse;
import com.quickbite.customer.exception.ResourceNotFoundException;
import com.quickbite.customer.model.Customer;
import com.quickbite.customer.model.CustomerAddress;
import com.quickbite.customer.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Saved addresses. Only the profile's owner (or an admin) can see or change them. */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerService customerService;

    @Transactional
    public AddressResponse addAddress(UUID customerId, AddressRequest request) {
        Customer customer = customerService.findAccessible(customerId);

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .label(request.getLabel())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .postalCode(request.getPostalCode())
                .build();

        CustomerAddress saved = addressRepository.save(address);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listByCustomer(UUID customerId) {
        customerService.findAccessible(customerId);
        return addressRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeAddress(UUID customerId, UUID addressId) {
        customerService.findAccessible(customerId);
        CustomerAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));
        if (!address.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Address not found for customer: " + customerId);
        }
        addressRepository.delete(address);
    }

    private AddressResponse toResponse(CustomerAddress address) {
        return AddressResponse.builder()
                .id(address.getId())
                .customerId(address.getCustomer().getId())
                .label(address.getLabel())
                .addressLine(address.getAddressLine())
                .city(address.getCity())
                .postalCode(address.getPostalCode())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
