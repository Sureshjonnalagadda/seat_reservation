package com.paytm.reservation.common.exception;

public class PerUserLimitExceededException extends RuntimeException {

    public PerUserLimitExceededException() {
        super("Per-user seat limit exceeded for this show");
    }
}
