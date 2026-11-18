package com.quickbite.order.kafka;

import com.quickbite.messaging.Outbox;
import com.quickbite.order.domain.Order;
import com.quickbite.order.service.OrderPlaced;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes {@code order.placed} to the transactional outbox.
 * <p>
 * A plain {@link EventListener} runs synchronously inside the transaction that saves the
 * order, so the order row and the event commit (or roll back) together. The outbox relay
 * then sends the event to Kafka.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    static final String ORDER_PLACED = "order.placed";

    private final Outbox outbox;

    @EventListener
    public void onOrderPlaced(OrderPlaced placed) {
        Order order = placed.order();
        OrderPlacedEvent event = new OrderPlacedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getItems().stream()
                        .map(i -> new OrderPlacedEvent.Item(i.getMenuItemId(), i.getMenuItemName(),
                                i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                order.getDeliveryAddress(),
                order.getTotalAmount(),
                Instant.now());
        outbox.publish(ORDER_PLACED, order.getId().toString(), event);
    }
}
