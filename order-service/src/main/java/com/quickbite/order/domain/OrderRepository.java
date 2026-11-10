package com.quickbite.order.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    /** Single UPDATE statement for event-driven status changes (no read-modify-write). */
    @Transactional
    @Modifying
    @Query("update Order o set o.status = :status, o.updatedAt = :now where o.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") OrderStatus status, @Param("now") Instant now);
}
