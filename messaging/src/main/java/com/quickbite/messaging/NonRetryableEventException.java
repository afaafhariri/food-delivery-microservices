package com.quickbite.messaging;

/**
 * Thrown by a listener for an event that can never be processed (malformed payload,
 * missing required field). The record goes straight to {@code <topic>.DLT} without retries.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
