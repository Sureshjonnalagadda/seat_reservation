package com.paytm.reservation.common.exception;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Idempotency key is required (header Idempotency-Key or body idempotency_key)");
    }
}
