package com.quickbite.restaurant.service;

import com.quickbite.restaurant.dto.MenuItemRequest;
import com.quickbite.restaurant.dto.MenuItemResponse;
import com.quickbite.restaurant.exception.ResourceNotFoundException;
import com.quickbite.restaurant.kafka.MenuEventProducer;
import com.quickbite.restaurant.model.MenuItem;
import com.quickbite.restaurant.model.Restaurant;
import com.quickbite.restaurant.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;
    private final MenuEventProducer menuEventProducer;

    @Transactional
    public MenuItemResponse addMenuItem(UUID restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantService.findRestaurantOrThrow(restaurantId);

        MenuItem menuItem = MenuItem.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .available(true)
                .build();

        MenuItem saved = menuItemRepository.save(menuItem);
        log.info("Added menu item: id={}, restaurantId={}", saved.getId(), restaurantId);

        menuEventProducer.publishMenuEvent(restaurantId, saved.getId(), MenuEventProducer.MenuAction.CREATED);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenuItems(UUID restaurantId) {
        restaurantService.findRestaurantOrThrow(restaurantId);
        return menuItemRepository.findByRestaurantId(restaurantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public MenuItemResponse updateMenuItem(UUID restaurantId, UUID itemId, MenuItemRequest request) {
        restaurantService.findRestaurantOrThrow(restaurantId);

        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemId));

        menuItem.setName(request.getName());
        menuItem.setDescription(request.getDescription());
        menuItem.setPrice(request.getPrice());
        menuItem.setCategory(request.getCategory());

        MenuItem updated = menuItemRepository.save(menuItem);
        log.info("Updated menu item: id={}, restaurantId={}", itemId, restaurantId);

        menuEventProducer.publishMenuEvent(restaurantId, itemId, MenuEventProducer.MenuAction.UPDATED);

        return mapToResponse(updated);
    }

    @Transactional
    public void removeMenuItem(UUID restaurantId, UUID itemId) {
        restaurantService.findRestaurantOrThrow(restaurantId);

        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemId));

        menuItemRepository.delete(menuItem);
        log.info("Removed menu item: id={}, restaurantId={}", itemId, restaurantId);

        menuEventProducer.publishMenuEvent(restaurantId, itemId, MenuEventProducer.MenuAction.DELETED);
    }

    private MenuItemResponse mapToResponse(MenuItem menuItem) {
        return MenuItemResponse.builder()
                .id(menuItem.getId())
                .restaurantId(menuItem.getRestaurant().getId())
                .name(menuItem.getName())
                .description(menuItem.getDescription())
                .price(menuItem.getPrice())
                .category(menuItem.getCategory())
                .available(menuItem.isAvailable())
                .createdAt(menuItem.getCreatedAt())
                .updatedAt(menuItem.getUpdatedAt())
                .build();
    }
}
