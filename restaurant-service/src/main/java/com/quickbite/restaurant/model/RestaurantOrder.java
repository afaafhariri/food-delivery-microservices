package com.quickbite.restaurant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An order placed with one of this service's restaurants (copied from order.placed). */
@Entity
@Table(name = "restaurant_orders")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantOrder {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** The order's items as the JSON array order.placed carried. */
    @Column(nullable = false)
    private String items = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantOrderStatus status;

    private String reason;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
