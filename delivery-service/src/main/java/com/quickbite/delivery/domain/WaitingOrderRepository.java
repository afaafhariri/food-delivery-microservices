package com.quickbite.delivery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitingOrderRepository extends JpaRepository<WaitingOrder, UUID> {

    List<WaitingOrder> findAllByOrderByWaitingSinceAscOrderIdAsc();

    /** Puts an order on the waiting list; a second call for the same order changes nothing. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO waiting_orders (order_id) VALUES (:orderId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int enqueue(@Param("orderId") UUID orderId);

    /**
     * Claims the longest-waiting order. {@code SKIP LOCKED} lets several instances work the
     * list at once, each on a different order. Must run inside a transaction.
     */
    @Query(value = """
            SELECT * FROM waiting_orders
            ORDER BY waiting_since, order_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<WaitingOrder> lockOldest();

    /**
     * Takes an order off the list. Runs at once (not at flush time) and waits for a
     * transaction that has the row claimed, so a cancellation sees that transaction's result.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM waiting_orders WHERE order_id = :orderId", nativeQuery = true)
    int remove(@Param("orderId") UUID orderId);
}
