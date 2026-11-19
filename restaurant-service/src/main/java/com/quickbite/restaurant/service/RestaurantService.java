package com.quickbite.restaurant.service;

import com.quickbite.restaurant.dto.PagedResponse;
import com.quickbite.restaurant.dto.RestaurantRequest;
import com.quickbite.restaurant.dto.RestaurantResponse;
import com.quickbite.restaurant.exception.ResourceNotFoundException;
import com.quickbite.restaurant.model.Restaurant;
import com.quickbite.restaurant.repository.RestaurantRepository;
import com.quickbite.security.CurrentUser;
import com.quickbite.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CurrentUser currentUser;

    @Transactional
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        currentUser.requireAnyRole(Roles.RESTAURANT);
        Restaurant restaurant = Restaurant.builder()
                .ownerId(currentUser.id())
                .name(request.getName())
                .address(request.getAddress())
                .cuisineType(request.getCuisineType())
                .operatingHours(request.getOperatingHours())
                .active(true)
                .build();
        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Created restaurant: id={}, name={}", saved.getId(), saved.getName());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RestaurantResponse> getAllRestaurants(int page, int size, String cuisineType) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Restaurant> restaurantPage;

        if (cuisineType != null && !cuisineType.isBlank()) {
            restaurantPage = restaurantRepository.findByCuisineTypeAndActiveTrue(cuisineType, pageable);
        } else {
            restaurantPage = restaurantRepository.findByActiveTrue(pageable);
        }

        return PagedResponse.<RestaurantResponse>builder()
                .content(restaurantPage.getContent().stream().map(this::mapToResponse).toList())
                .page(restaurantPage.getNumber())
                .size(restaurantPage.getSize())
                .totalElements(restaurantPage.getTotalElements())
                .totalPages(restaurantPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(UUID id) {
        Restaurant restaurant = findRestaurantOrThrow(id);
        return mapToResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(UUID id, RestaurantRequest request) {
        Restaurant restaurant = findOwnedRestaurant(id);
        restaurant.setName(request.getName());
        restaurant.setAddress(request.getAddress());
        restaurant.setCuisineType(request.getCuisineType());
        restaurant.setOperatingHours(request.getOperatingHours());
        Restaurant updated = restaurantRepository.save(restaurant);
        log.info("Updated restaurant: id={}", updated.getId());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteRestaurant(UUID id) {
        Restaurant restaurant = findOwnedRestaurant(id);
        restaurant.setActive(false);
        restaurantRepository.save(restaurant);
        log.info("Soft-deleted restaurant: id={}", id);
    }

    public Restaurant findRestaurantOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
    }

    /** The restaurant, if the caller is its owner or an admin; otherwise 403. */
    public Restaurant findOwnedRestaurant(UUID id) {
        Restaurant restaurant = findRestaurantOrThrow(id);
        currentUser.requireOwnerOrAdmin(restaurant.getOwnerId());
        return restaurant;
    }

    private RestaurantResponse mapToResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .cuisineType(restaurant.getCuisineType())
                .operatingHours(restaurant.getOperatingHours())
                .active(restaurant.isActive())
                .ownerId(restaurant.getOwnerId())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
