package com.quickbite.delivery.kafka;

import com.quickbite.delivery.service.DeliveryService;
import com.quickbite.delivery.service.DeliveryStatusChanged;
import com.quickbite.messaging.EventHeaders;
import com.quickbite.messaging.Inbox;
import com.quickbite.messaging.NonRetryableEventException;
import com.quickbite.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka edge of the delivery service: consumes {@code order.placed} and
 * {@code order.cancelled}, emits {@code delivery.status.updated} through the transactional outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEvents {

    static final String STATUS_TOPIC = "delivery.status.updated";
    static final String GROUP = "delivery-service-group";

    private final DeliveryService deliveries;
    private final Outbox outbox;
    private final Inbox inbox;
    private final ObjectMapper json;

    record OrderPlacedEvent(UUID orderId) {
    }

    record OrderCancelledEvent(UUID orderId, String reason) {
    }

    /** Wire format unchanged from the Go producer. */
    record DeliveryStatusEvent(UUID deliveryId, UUID orderId, UUID driverId, String status, Instant timestamp) {
    }

    /**
     * The inbox entry, the delivery and its ASSIGNED event share one transaction: a crash
     * anywhere means the order is retried from scratch, never half-assigned.
     */
    @Transactional
    @KafkaListener(topics = "order.placed", groupId = GROUP)
    public void onOrderPlaced(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        OrderPlacedEvent event;
        try {
            event = json.readValue(record.value(), OrderPlacedEvent.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new NonRetryableEventException("malformed order.placed payload: " + record.value(), e);
        }
        if (event.orderId() == null) {
            throw new NonRetryableEventException("order.placed event missing orderId: " + record.value());
        }
        deliveries.assign(event.orderId());
    }

    /** Frees the driver of a cancelled order (see {@link DeliveryService#cancelForOrder}). */
    @Transactional
    @KafkaListener(topics = "order.cancelled", groupId = GROUP)
    public void onOrderCancelled(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        OrderCancelledEvent event;
        try {
            event = json.readValue(record.value(), OrderCancelledEvent.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new NonRetryableEventException("malformed order.cancelled payload: " + record.value(), e);
        }
        if (event.orderId() == null) {
            throw new NonRetryableEventException("order.cancelled event missing orderId: " + record.value());
        }
        log.info("order {} cancelled ({})", event.orderId(), event.reason());
        deliveries.cancelForOrder(event.orderId());
    }

    /** Runs synchronously inside the transaction that changed the delivery. */
    @EventListener
    public void publish(DeliveryStatusChanged change) {
        var event = new DeliveryStatusEvent(change.deliveryId(), change.orderId(), change.driverId(),
                change.status(), Instant.now());
        outbox.publish(STATUS_TOPIC, change.deliveryId().toString(), event);
        log.info("queued {} deliveryId={} status={}", STATUS_TOPIC, change.deliveryId(), change.status());
    }
}
