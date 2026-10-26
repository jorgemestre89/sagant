package com.sagant.notifications.exceptions;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
