package com.quickbite.messaging;

import java.util.List;

/**
 * Every QuickBite Kafka topic. The broker no longer creates topics on first use: each service
 * declares this whole list at startup ({@link MessagingAutoConfiguration}), so whichever service
 * starts first creates them with the agreed partitions and retention, and every topic, including
 * its {@code <topic>.DLT}, exists before anyone produces to it or listens on it.
 */
public final class Topics {

    public static final String ORDER_PLACED = "order.placed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String DELIVERY_STATUS_UPDATED = "delivery.status.updated";
    public static final String RESTAURANT_ORDER_RESPONSE = "restaurant.order.response";
    public static final String RESTAURANT_MENU_UPDATED = "restaurant.menu.updated";
    public static final String CUSTOMER_NOTIFICATION = "customer.notification";

    /** Suffix of each topic's dead-letter topic (see the consumer error handler). */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    public static final List<String> ALL = List.of(ORDER_PLACED, ORDER_CANCELLED, DELIVERY_STATUS_UPDATED,
            RESTAURANT_ORDER_RESPONSE, RESTAURANT_MENU_UPDATED, CUSTOMER_NOTIFICATION);

    private Topics() {
    }
}
