package com.paytm.reservation.common.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency-Key header and body idempotency_key must match when both are provided");
    }
}
