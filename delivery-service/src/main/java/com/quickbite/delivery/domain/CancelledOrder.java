package com.quickbite.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * An order announced on {@code order.cancelled}. {@code order.placed} and
 * {@code order.cancelled} are separate topics, so Kafka does not order them: the
 * cancellation can arrive first. Remembering it lets a late {@code order.placed} be ignored
 * instead of tying up a driver for a cancelled order.
 */
@Entity
@Table(name = "cancelled_orders")
@Getter
@NoArgsConstructor
public class CancelledOrder {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @CreationTimestamp
    @Column(name = "cancelled_at", updatable = false)
    private Instant cancelledAt;

    public CancelledOrder(UUID orderId) {
        this.orderId = orderId;
    }
}
