package com.quickbite.restaurant.repository;

import com.quickbite.restaurant.model.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Page<Restaurant> findByActiveTrue(Pageable pageable);

    Page<Restaurant> findByCuisineTypeAndActiveTrue(String cuisineType, Pageable pageable);
}
