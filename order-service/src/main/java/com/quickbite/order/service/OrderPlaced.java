package com.quickbite.order.service;

import com.quickbite.order.domain.Order;

/** Spring application event, published inside the TX and relayed to Kafka after commit. */
public record OrderPlaced(Order order) {
}
