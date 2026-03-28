package com.quickbite.customer.repository;

import com.quickbite.customer.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {

    List<OrderHistory> findByCustomerIdOrderByOrderDateDesc(UUID customerId);
}
