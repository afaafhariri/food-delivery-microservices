package com.quickbite.delivery.service;

import java.util.UUID;

/** Spring application event, written to the outbox inside the transaction that changed the delivery. */
public record DeliveryStatusChanged(UUID deliveryId, UUID orderId, UUID driverId, String status) {
}
