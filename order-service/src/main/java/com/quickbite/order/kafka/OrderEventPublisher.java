package com.quickbite.order.kafka;

import com.quickbite.order.domain.Order;
import com.quickbite.order.service.OrderPlaced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/** Relays committed domain events to Kafka; rolled-back orders are never announced. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    static final String ORDER_PLACED = "order.placed";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    @TransactionalEventListener
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

        String key = order.getId().toString();
        kafka.send(ORDER_PLACED, key, json.writeValueAsString(event)).whenComplete((result, error) -> {
            if (error != null) {
                log.error("failed to publish {} orderId={}", ORDER_PLACED, key, error);
            } else {
                log.info("published {} orderId={}", ORDER_PLACED, key);
            }
        });
    }
}
