package com.quickbite.restaurant.repository;

import com.quickbite.restaurant.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findByRestaurantId(UUID restaurantId);

    Optional<MenuItem> findByRestaurantIdAndId(UUID restaurantId, UUID id);
}
