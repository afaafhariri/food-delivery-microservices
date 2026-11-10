package com.quickbite.delivery.domain;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    @EntityGraph(attributePaths = "driver")
    Optional<Delivery> findWithDriverById(UUID id);

    @EntityGraph(attributePaths = "driver")
    Optional<Delivery> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    @Override
    @EntityGraph(attributePaths = "driver")
    List<Delivery> findAll(Specification<Delivery> spec, Sort sort);
}
