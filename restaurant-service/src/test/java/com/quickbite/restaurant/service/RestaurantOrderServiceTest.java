package com.quickbite.restaurant.service;

import com.quickbite.restaurant.exception.ConflictException;
import com.quickbite.restaurant.kafka.OrderResponseProducer;
import com.quickbite.restaurant.kafka.OrderResponseProducer.OrderStatus;
import com.quickbite.restaurant.model.RestaurantOrder;
import com.quickbite.restaurant.model.RestaurantOrderStatus;
import com.quickbite.restaurant.repository.RestaurantOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Accepting and rejecting orders, and keeping the restaurant's order list in step with Kafka. */
class RestaurantOrderServiceTest {

    private final RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
    private final RestaurantService restaurants = mock(RestaurantService.class);
    private final OrderResponseProducer responses = mock(OrderResponseProducer.class);
    private final RestaurantOrderService service =
            new RestaurantOrderService(orders, restaurants, responses, JsonMapper.builder().build());

    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void acceptingAWaitingOrderConfirmsIt() {
        RestaurantOrder order = stored(RestaurantOrderStatus.PLACED);

        var response = service.accept(restaurantId, order.getOrderId());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.items()).singleElement().satisfies(line -> {
            assertThat(line.menuItemName()).isEqualTo("Kottu");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.unitPrice()).isEqualByComparingTo("750.00");
        });
        verify(responses).publishOrderResponse(order.getOrderId(), OrderStatus.CONFIRMED, null);
    }

    @Test
    void rejectingPassesTheReasonOn() {
        RestaurantOrder order = stored(RestaurantOrderStatus.PLACED);

        var response = service.reject(restaurantId, order.getOrderId(), "  kitchen closed ");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reason()).isEqualTo("kitchen closed");
        verify(responses).publishOrderResponse(order.getOrderId(), OrderStatus.REJECTED, "kitchen closed");
    }

    @Test
    void onlyWaitingOrdersCanBeAnswered() {
        RestaurantOrder cancelled = stored(RestaurantOrderStatus.CANCELLED);
        RestaurantOrder accepted = stored(RestaurantOrderStatus.ACCEPTED);

        assertThatThrownBy(() -> service.accept(restaurantId, cancelled.getOrderId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reject(restaurantId, accepted.getOrderId(), "too late"))
                .isInstanceOf(ConflictException.class);
        verify(responses, never()).publishOrderResponse(any(), any(), any());
    }

    @Test
    void someoneElsesRestaurantIsRefusedBeforeAnythingChanges() {
        RestaurantOrder order = stored(RestaurantOrderStatus.PLACED);
        when(restaurants.findOwnedRestaurant(restaurantId)).thenThrow(new AccessDeniedException("not yours"));

        assertThatThrownBy(() -> service.accept(restaurantId, order.getOrderId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(order.getStatus()).isEqualTo(RestaurantOrderStatus.PLACED);
        verify(responses, never()).publishOrderResponse(any(), any(), anyString());
    }

    @Test
    void aCancellationThatArrivesFirstWinsOverALateOrderPlaced() {
        UUID orderId = UUID.randomUUID();
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        service.recordCancelled(orderId, restaurantId, "cancelled by customer");

        verify(orders).save(org.mockito.ArgumentMatchers.argThat(o ->
                o.getStatus() == RestaurantOrderStatus.CANCELLED && o.getOrderId().equals(orderId)));
        when(orders.existsById(orderId)).thenReturn(true);
        service.recordPlaced(orderId, restaurantId, UUID.randomUUID(), "x", BigDecimal.ONE, "[]", Instant.now());
        verify(orders, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void aRejectedOrderStaysRejectedWhenItsCancellationArrives() {
        RestaurantOrder order = stored(RestaurantOrderStatus.REJECTED);
        when(orders.findById(order.getOrderId())).thenReturn(Optional.of(order));

        service.recordCancelled(order.getOrderId(), restaurantId, "rejected by restaurant");

        assertThat(order.getStatus()).isEqualTo(RestaurantOrderStatus.REJECTED);
    }

    private RestaurantOrder stored(RestaurantOrderStatus status) {
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderId(UUID.randomUUID());
        order.setRestaurantId(restaurantId);
        order.setStatus(status);
        order.setItems("[{\"menuItemId\":\"" + UUID.randomUUID()
                + "\",\"menuItemName\":\"Kottu\",\"quantity\":2,\"unitPrice\":750.00}]");
        when(orders.findByOrderIdAndRestaurantId(order.getOrderId(), restaurantId)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        return order;
    }
}
