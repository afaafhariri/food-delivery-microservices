package com.quickbite.order.service;

import com.quickbite.order.domain.Order;
import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import com.quickbite.order.grpc.OrderValidationClient;
import com.quickbite.order.web.ApiException;
import com.quickbite.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every path to CANCELLED announces it exactly once ({@link OrderCancelled} → {@code order.cancelled}). */
class OrderCancellationTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CurrentUser admin = admin();
    private final OrderService service = new OrderService(repository, mock(OrderValidationClient.class), events,
            mock(TransactionTemplate.class), admin);

    private static CurrentUser admin() {
        CurrentUser user = mock(CurrentUser.class);
        when(user.isAdmin()).thenReturn(true);
        when(user.ownsOrAdmin(any())).thenReturn(true);
        return user;
    }

    @Test
    void customerCancellationPublishesOrderCancelled() {
        Order order = order(OrderStatus.PLACED);

        service.cancel(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(new OrderCancelled(order, "cancelled by customer"));
    }

    @Test
    void onlyPlacedOrdersCanBeCancelledByTheCustomer() {
        Order order = order(OrderStatus.PICKED_UP);
        assertThatThrownBy(() -> service.cancel(order.getId())).isInstanceOf(ApiException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void statusUpdateToCancelledPublishesOnce() {
        Order order = order(OrderStatus.CONFIRMED);

        service.updateStatus(order.getId(), "CANCELLED");
        service.updateStatus(order.getId(), "CANCELLED");

        verify(events, times(1)).publishEvent(any(OrderCancelled.class));
    }

    @Test
    void pickedUpOrdersCanNoLongerBeCancelled() {
        Order order = order(OrderStatus.PICKED_UP);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), "CANCELLED"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(409));
        assertThat(service.cancelByRestaurant(order.getId(), "too late")).isTrue();   // ignored, not an error

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void restaurantRejectionPublishesWithItsReason() {
        Order order = order(OrderStatus.PLACED);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(service.cancelByRestaurant(order.getId(), "kitchen closed")).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(new OrderCancelled(order, "kitchen closed"));
    }

    @Test
    void restaurantRejectionOfAnUnknownOrderReturnsFalse() {
        assertThat(service.cancelByRestaurant(UUID.randomUUID(), "x")).isFalse();
        verify(events, never()).publishEvent(any());
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setDeliveryAddress("1 Test St");
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(status);
        when(repository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));
        when(repository.saveAndFlush(order)).thenReturn(order);
        return order;
    }
}
