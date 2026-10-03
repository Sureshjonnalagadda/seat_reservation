package com.paytm.reservation.common.exception;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(long showId) {
        super("Show not found: " + showId);
    }
}
