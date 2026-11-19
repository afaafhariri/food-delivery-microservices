package com.quickbite.customer.kafka;

import com.quickbite.messaging.EventHeaders;
import com.quickbite.messaging.Inbox;
import com.quickbite.messaging.NonRetryableEventException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivers {@code customer.notification}. For now "delivering" means logging it: the message, its
 * type and the customer show up in this service's log (with the trace id of the order that caused
 * it). Replace the log line with an email/SMS/push client to send real notifications.
 * <p>
 * Its own consumer group, so it reads the topic independently of everything else and could move
 * to a separate notification service unchanged. Each notification is logged once (inbox).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSender {

    static final String GROUP = "notification-sender";

    private final Inbox inbox;
    private final ObjectMapper json;

    @Transactional
    @KafkaListener(topics = "customer.notification", groupId = GROUP)
    public void send(ConsumerRecord<String, String> record) {
        if (!inbox.firstDelivery(GROUP, EventHeaders.eventId(record))) {
            return;
        }
        JsonNode notification;
        try {
            notification = json.readTree(record.value());
        } catch (JacksonException e) {
            throw new NonRetryableEventException("malformed customer.notification payload: " + record.value(), e);
        }
        if (!notification.hasNonNull("customerId") || !notification.hasNonNull("message")) {
            throw new NonRetryableEventException("customer.notification without customerId or message: " + record.value());
        }
        log.info("NOTIFY customer={} type={}: {}", notification.get("customerId").asString(),
                notification.path("type").asString("UNKNOWN"), notification.get("message").asString());
    }
}
