package com.quickbite.delivery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findAllByOrderByCreatedAtAsc();

    List<Driver> findByAvailableOrderByCreatedAtAsc(boolean available);

    /**
     * Claims the longest-registered free driver. {@code SKIP LOCKED} lets concurrent
     * assignments pick different drivers instead of racing for (or blocking on) the same row.
     * Must run inside a transaction.
     */
    @Query(value = """
            SELECT * FROM drivers
            WHERE available = true
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Driver> lockNextAvailable();
}
