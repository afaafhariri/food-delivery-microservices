package com.quickbite.restaurant.exception;

/** The request conflicts with the current state, e.g. accepting an order that was cancelled (409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
