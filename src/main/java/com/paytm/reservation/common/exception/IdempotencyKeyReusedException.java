package com.paytm.reservation.common.exception;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used with a different request");
    }
}
