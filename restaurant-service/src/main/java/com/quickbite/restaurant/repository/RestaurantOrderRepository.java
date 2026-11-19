package com.quickbite.restaurant.repository;

import com.quickbite.restaurant.model.RestaurantOrder;
import com.quickbite.restaurant.model.RestaurantOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    List<RestaurantOrder> findByRestaurantIdOrderByPlacedAtDesc(UUID restaurantId);

    List<RestaurantOrder> findByRestaurantIdAndStatusOrderByPlacedAtDesc(UUID restaurantId, RestaurantOrderStatus status);

    Optional<RestaurantOrder> findByOrderIdAndRestaurantId(UUID orderId, UUID restaurantId);
}
