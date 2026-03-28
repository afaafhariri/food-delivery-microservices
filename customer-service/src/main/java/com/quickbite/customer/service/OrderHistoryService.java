package com.quickbite.customer.service;

import com.quickbite.customer.dto.OrderHistoryResponse;
import com.quickbite.customer.model.OrderHistory;
import com.quickbite.customer.repository.OrderHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;

    @Transactional
    public OrderHistory save(OrderHistory orderHistory) {
        return orderHistoryRepository.save(orderHistory);
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> listByCustomerId(UUID customerId) {
        return orderHistoryRepository.findByCustomerIdOrderByOrderDateDesc(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrderHistoryResponse toResponse(OrderHistory history) {
        return OrderHistoryResponse.builder()
                .orderId(history.getOrderId())
                .restaurantId(history.getRestaurantId())
                .deliveryAddress(history.getDeliveryAddress())
                .orderDate(history.getOrderDate())
                .build();
    }
}
