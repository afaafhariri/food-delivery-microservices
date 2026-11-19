package com.quickbite.customer.exception;

/** The request conflicts with existing data, e.g. a second profile for the same account (409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
