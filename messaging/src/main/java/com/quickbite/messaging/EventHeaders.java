package com.quickbite.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/** Kafka headers added by the outbox relay. */
public final class EventHeaders {

    /** Unique id of the event (UUID). The same id is sent again if a send is retried. */
    public static final String EVENT_ID = "event-id";

    private EventHeaders() {
    }

    /** The record's event id, or {@code null} for records published without the outbox. */
    public static String eventId(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(EVENT_ID);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
