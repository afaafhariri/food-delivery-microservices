package com.quickbite.restaurant.service;

import com.quickbite.restaurant.dto.RestaurantOrderDtos.Line;
import com.quickbite.restaurant.dto.RestaurantOrderDtos.RestaurantOrderResponse;
import com.quickbite.restaurant.exception.ConflictException;
import com.quickbite.restaurant.exception.ResourceNotFoundException;
import com.quickbite.restaurant.kafka.OrderResponseProducer;
import com.quickbite.restaurant.model.RestaurantOrder;
import com.quickbite.restaurant.model.RestaurantOrderStatus;
import com.quickbite.restaurant.repository.RestaurantOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orders placed with this service's restaurants, and the restaurant's answer to them.
 * <p>
 * The list is filled from {@code order.placed} and {@code order.cancelled} (no user involved:
 * those methods do no access checks). Listing, accepting and rejecting are HTTP calls by the
 * restaurant's owner or an admin. An answer is published as {@code restaurant.order.response}
 * through the outbox, in the same transaction as the status change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantOrderService {

    private final RestaurantOrderRepository orders;
    private final RestaurantService restaurants;
    private final OrderResponseProducer responses;
    private final ObjectMapper json;

    // ── From Kafka ───────────────────────────────────────────────────────────

    /**
     * Records a placed order. Idempotent; and if the order's cancellation arrived first (the two
     * topics are not ordered relative to each other), the CANCELLED row is kept.
     */
    @Transactional
    public void recordPlaced(UUID orderId, UUID restaurantId, UUID customerId, String deliveryAddress,
                             BigDecimal totalAmount, String itemsJson, Instant placedAt) {
        if (orders.existsById(orderId)) {
            log.info("order {} already known, ignoring order.placed", orderId);
            return;
        }
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderId(orderId);
        order.setRestaurantId(restaurantId);
        order.setCustomerId(customerId);
        order.setDeliveryAddress(deliveryAddress);
        order.setTotalAmount(totalAmount);
        order.setItems(itemsJson == null ? "[]" : itemsJson);
        order.setPlacedAt(placedAt);
        order.setStatus(RestaurantOrderStatus.PLACED);
        orders.save(order);
        log.info("order {} placed with restaurant {}, waiting for its answer", orderId, restaurantId);
    }

    /** The order was cancelled (by the customer, an admin, or after our own rejection). Idempotent. */
    @Transactional
    public void recordCancelled(UUID orderId, UUID restaurantId, String reason) {
        Optional<RestaurantOrder> found = orders.findById(orderId);
        if (found.isEmpty()) {
            // Cancellation before order.placed: remember it so a late order.placed is ignored.
            RestaurantOrder order = new RestaurantOrder();
            order.setOrderId(orderId);
            order.setRestaurantId(restaurantId);
            order.setStatus(RestaurantOrderStatus.CANCELLED);
            order.setReason(reason);
            orders.save(order);
            return;
        }
        RestaurantOrder order = found.get();
        if (order.getStatus() == RestaurantOrderStatus.PLACED || order.getStatus() == RestaurantOrderStatus.ACCEPTED) {
            order.setStatus(RestaurantOrderStatus.CANCELLED);
            order.setReason(reason);
            log.info("order {} cancelled: {}", orderId, reason);
        }
        // REJECTED stays REJECTED: the cancellation is the consequence of our own rejection.
    }

    // ── HTTP (restaurant owner or admin) ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RestaurantOrderResponse> list(UUID restaurantId, RestaurantOrderStatus status) {
        restaurants.findOwnedRestaurant(restaurantId);
        List<RestaurantOrder> found = status == null
                ? orders.findByRestaurantIdOrderByPlacedAtDesc(restaurantId)
                : orders.findByRestaurantIdAndStatusOrderByPlacedAtDesc(restaurantId, status);
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RestaurantOrderResponse accept(UUID restaurantId, UUID orderId) {
        RestaurantOrder order = waitingOrder(restaurantId, orderId);
        order.setStatus(RestaurantOrderStatus.ACCEPTED);
        responses.publishOrderResponse(orderId, OrderResponseProducer.OrderStatus.CONFIRMED, null);
        log.info("restaurant {} accepted order {}", restaurantId, orderId);
        return toResponse(orders.saveAndFlush(order));
    }

    @Transactional
    public RestaurantOrderResponse reject(UUID restaurantId, UUID orderId, String reason) {
        RestaurantOrder order = waitingOrder(restaurantId, orderId);
        order.setStatus(RestaurantOrderStatus.REJECTED);
        order.setReason(reason.strip());
        responses.publishOrderResponse(orderId, OrderResponseProducer.OrderStatus.REJECTED, reason.strip());
        log.info("restaurant {} rejected order {}: {}", restaurantId, orderId, reason);
        return toResponse(orders.saveAndFlush(order));
    }

    /** An order of this restaurant that still waits for an answer; 403 / 404 / 409 otherwise. */
    private RestaurantOrder waitingOrder(UUID restaurantId, UUID orderId) {
        restaurants.findOwnedRestaurant(restaurantId);
        RestaurantOrder order = orders.findByOrderIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (order.getStatus() != RestaurantOrderStatus.PLACED) {
            throw new ConflictException("order " + orderId + " is already " + order.getStatus());
        }
        return order;
    }

    private RestaurantOrderResponse toResponse(RestaurantOrder order) {
        return new RestaurantOrderResponse(order.getOrderId(), order.getRestaurantId(), order.getCustomerId(),
                order.getStatus().name(), order.getReason(), order.getTotalAmount(), order.getDeliveryAddress(),
                lines(order.getItems()), order.getPlacedAt(), order.getUpdatedAt());
    }

    /** The items JSON is order.placed's (camelCase); responses use snake_case records. */
    private List<Line> lines(String itemsJson) {
        List<Line> lines = new ArrayList<>();
        for (JsonNode item : json.readTree(itemsJson)) {
            lines.add(new Line(
                    item.hasNonNull("menuItemId") ? UUID.fromString(item.get("menuItemId").asString()) : null,
                    item.path("menuItemName").asString(null),
                    item.path("quantity").asInt(0),
                    item.hasNonNull("unitPrice") ? item.get("unitPrice").decimalValue() : null));
        }
        return lines;
    }
}
