package com.quickbite.delivery.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CancelledOrderRepository extends JpaRepository<CancelledOrder, UUID> {
}
