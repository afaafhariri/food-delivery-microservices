package com.quickbite.restaurant.controller;

import com.quickbite.restaurant.dto.MenuItemRequest;
import com.quickbite.restaurant.dto.MenuItemResponse;
import com.quickbite.restaurant.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/menu-items")
@RequiredArgsConstructor
@Tag(name = "Menu Items", description = "Menu item management endpoints")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping
    @Operation(summary = "Add a menu item to a restaurant")
    public ResponseEntity<MenuItemResponse> addMenuItem(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse response = menuItemService.addMenuItem(restaurantId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List all menu items for a restaurant")
    public ResponseEntity<List<MenuItemResponse>> getMenuItems(@PathVariable UUID restaurantId) {
        List<MenuItemResponse> response = menuItemService.getMenuItems(restaurantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update a menu item")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse response = menuItemService.updateMenuItem(restaurantId, itemId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove a menu item")
    public ResponseEntity<Void> removeMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId) {
        menuItemService.removeMenuItem(restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }
}
