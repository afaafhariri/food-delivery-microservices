package com.quickbite.restaurant.model;

/** Where an order stands from the restaurant's point of view. */
public enum RestaurantOrderStatus {
    /** Waiting for the restaurant to accept or reject it. */
    PLACED,
    /** The restaurant confirmed it (order-service moves the order to CONFIRMED). */
    ACCEPTED,
    /** The restaurant refused it (order-service cancels the order, delivery frees the driver). */
    REJECTED,
    /** The customer or an admin cancelled it. */
    CANCELLED
}
