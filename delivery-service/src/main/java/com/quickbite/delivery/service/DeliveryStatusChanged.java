package com.quickbite.delivery.service;

import java.util.UUID;

/** Spring application event; relayed to Kafka only after the transaction commits. */
public record DeliveryStatusChanged(UUID deliveryId, UUID orderId, UUID driverId, String status) {
}
