package com.quickbite.order.service;

import com.quickbite.order.domain.Order;

/** Spring application event, published inside the transaction that saves the order. */
public record OrderPlaced(Order order) {
}
