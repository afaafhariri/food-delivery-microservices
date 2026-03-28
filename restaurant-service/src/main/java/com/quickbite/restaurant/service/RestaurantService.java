package com.quickbite.restaurant.service;

import com.quickbite.restaurant.dto.PagedResponse;
import com.quickbite.restaurant.dto.RestaurantRequest;
import com.quickbite.restaurant.dto.RestaurantResponse;
import com.quickbite.restaurant.exception.ResourceNotFoundException;
import com.quickbite.restaurant.model.Restaurant;
import com.quickbite.restaurant.repository.RestaurantRepository;
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

    @Transactional
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
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
        Restaurant restaurant = findRestaurantOrThrow(id);
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
        Restaurant restaurant = findRestaurantOrThrow(id);
        restaurant.setActive(false);
        restaurantRepository.save(restaurant);
        log.info("Soft-deleted restaurant: id={}", id);
    }

    public Restaurant findRestaurantOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
    }

    private RestaurantResponse mapToResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .cuisineType(restaurant.getCuisineType())
                .operatingHours(restaurant.getOperatingHours())
                .active(restaurant.isActive())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
