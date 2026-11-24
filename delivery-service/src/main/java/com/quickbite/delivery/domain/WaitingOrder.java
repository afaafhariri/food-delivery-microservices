package com.quickbite.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A placed order that found every driver busy. It waits here, oldest first, until a driver
 * becomes free; then it gets a delivery and leaves the table. A cancellation removes it too.
 */
@Entity
@Table(name = "waiting_orders")
@Getter
@NoArgsConstructor
public class WaitingOrder {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "waiting_since", insertable = false, updatable = false)
    private Instant waitingSince;

    public WaitingOrder(UUID orderId, Instant waitingSince) {
        this.orderId = orderId;
        this.waitingSince = waitingSince;
    }
}
