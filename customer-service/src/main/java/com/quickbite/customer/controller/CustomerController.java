package com.quickbite.customer.controller;

import com.quickbite.customer.dto.AddressRequest;
import com.quickbite.customer.dto.AddressResponse;
import com.quickbite.customer.dto.CustomerRequest;
import com.quickbite.customer.dto.CustomerResponse;
import com.quickbite.customer.dto.OrderHistoryResponse;
import com.quickbite.customer.service.AddressService;
import com.quickbite.customer.service.CustomerService;
import com.quickbite.customer.service.OrderHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management endpoints")
public class CustomerController {

    private final CustomerService customerService;
    private final AddressService addressService;
    private final OrderHistoryService orderHistoryService;

    @PostMapping
    @Operation(summary = "Register a new customer")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all active customers")
    public ResponseEntity<List<CustomerResponse>> listAll() {
        return ResponseEntity.ok(customerService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer profile by ID")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer profile")
    public ResponseEntity<CustomerResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a customer")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        customerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add an address to a customer")
    public ResponseEntity<AddressResponse> addAddress(@PathVariable UUID id,
                                                       @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.addAddress(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "List all addresses for a customer")
    public ResponseEntity<List<AddressResponse>> listAddresses(@PathVariable UUID id) {
        return ResponseEntity.ok(addressService.listByCustomer(id));
    }

    @DeleteMapping("/{id}/addresses/{addrId}")
    @Operation(summary = "Remove an address from a customer")
    public ResponseEntity<Void> removeAddress(@PathVariable UUID id, @PathVariable UUID addrId) {
        addressService.removeAddress(id, addrId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/order-history")
    @Operation(summary = "List order history for a customer")
    public ResponseEntity<List<OrderHistoryResponse>> listOrderHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(orderHistoryService.listByCustomerId(id));
    }
}
