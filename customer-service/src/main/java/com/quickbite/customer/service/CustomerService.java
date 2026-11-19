package com.quickbite.customer.service;

import com.quickbite.customer.dto.CustomerRequest;
import com.quickbite.customer.dto.CustomerResponse;
import com.quickbite.customer.exception.ConflictException;
import com.quickbite.customer.exception.ResourceNotFoundException;
import com.quickbite.customer.model.Customer;
import com.quickbite.customer.repository.CustomerRepository;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer profiles. A profile belongs to the Keycloak account that created it ({@code user_id});
 * each account has at most one. Customers see and change only their own profile; admins see all.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CurrentUser currentUser;

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        currentUser.requireAnyRole(Roles.CUSTOMER);
        // A customer account gets its own profile; an admin without the customer role creates an
        // admin-managed profile that no account owns.
        String userId = currentUser.hasRole(Roles.CUSTOMER) ? currentUser.id() : null;
        if (userId != null && customerRepository.existsByUserId(userId)) {
            throw new ConflictException("this account already has a customer profile (GET /api/customers/me)");
        }
        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .userId(userId)
                .active(true)
                .build();
        return toResponse(customerRepository.save(customer));
    }

    /** The caller's own profile. */
    @Transactional(readOnly = true)
    public CustomerResponse getMine() {
        return customerRepository.findByUserId(currentUser.id())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("this account has no customer profile yet"));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listAll() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("only admins can list all customers");
        }
        return customerRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return toResponse(findAccessible(id));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findAccessible(id);
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void softDelete(UUID id) {
        Customer customer = findAccessible(id);
        customer.setActive(false);
        customerRepository.save(customer);
    }

    /** The customer, if the caller owns the profile or is an admin; 404 if missing, 403 if not theirs. */
    public Customer findAccessible(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        currentUser.requireOwnerOrAdmin(customer.getUserId());
        return customer;
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .userId(customer.getUserId())
                .active(customer.isActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
