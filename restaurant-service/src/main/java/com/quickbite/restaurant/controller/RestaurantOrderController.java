package com.quickbite.restaurant.controller;

import com.quickbite.restaurant.dto.RestaurantOrderDtos.RejectRequest;
import com.quickbite.restaurant.dto.RestaurantOrderDtos.RestaurantOrderResponse;
import com.quickbite.restaurant.model.RestaurantOrderStatus;
import com.quickbite.restaurant.service.RestaurantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** A restaurant's incoming orders. Only the restaurant's owner (or an admin) can see and answer them. */
@RestController
@RequestMapping("/api/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
@Tag(name = "Restaurant orders", description = "Incoming orders: list, accept, reject")
public class RestaurantOrderController {

    private final RestaurantOrderService orders;

    @GetMapping
    @Operation(summary = "List the restaurant's orders, newest first, optionally by status (PLACED = waiting)")
    public List<RestaurantOrderResponse> list(@PathVariable UUID restaurantId,
                                              @RequestParam(required = false) RestaurantOrderStatus status) {
        return orders.list(restaurantId, status);
    }

    @PostMapping("/{orderId}/accept")
    @Operation(summary = "Accept a waiting order (the order becomes CONFIRMED)")
    public RestaurantOrderResponse accept(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        return orders.accept(restaurantId, orderId);
    }

    @PostMapping("/{orderId}/reject")
    @Operation(summary = "Reject a waiting order (the order is cancelled and its driver freed)")
    public RestaurantOrderResponse reject(@PathVariable UUID restaurantId, @PathVariable UUID orderId,
                                          @Valid @RequestBody RejectRequest request) {
        return orders.reject(restaurantId, orderId, request.reason());
    }
}
