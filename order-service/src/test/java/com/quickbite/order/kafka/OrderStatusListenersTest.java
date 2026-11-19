package com.quickbite.order.kafka;

import com.quickbite.messaging.Inbox;
import com.quickbite.order.domain.OrderRepository;
import com.quickbite.order.domain.OrderStatus;
import com.quickbite.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** restaurant.order.response: a confirmation only moves a waiting order; a rejection cancels it. */
class OrderStatusListenersTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService orders = mock(OrderService.class);
    private final Inbox inbox = mock(Inbox.class);
    private final OrderStatusListeners listeners =
            new OrderStatusListeners(repository, orders, inbox, JsonMapper.builder().build());

    private final UUID orderId = UUID.randomUUID();

    @Test
    void confirmationOnlyAppliesToAPlacedOrder() {
        when(inbox.firstDelivery(anyString(), any())).thenReturn(true);

        listeners.onRestaurantResponse(record("{\"orderId\":\"" + orderId + "\",\"status\":\"CONFIRMED\"}"));

        verify(repository).updateStatusIf(eq(orderId), eq(OrderStatus.PLACED), eq(OrderStatus.CONFIRMED), any());
        verify(repository, never()).updateStatus(any(), any(), any());
    }

    @Test
    void rejectionCancelsTheOrderWithTheRestaurantsReason() {
        when(inbox.firstDelivery(anyString(), any())).thenReturn(true);
        when(orders.cancelByRestaurant(orderId, "kitchen closed")).thenReturn(true);

        listeners.onRestaurantResponse(record(
                "{\"orderId\":\"" + orderId + "\",\"status\":\"REJECTED\",\"reason\":\"kitchen closed\"}"));

        verify(orders).cancelByRestaurant(orderId, "kitchen closed");
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("restaurant.order.response", 0, 0L, "key", value);
    }
}
