package com.paytm.reservation.common.exception;

public class DuplicateSeatNumbersException extends RuntimeException {

    public DuplicateSeatNumbersException() {
        super("Duplicate seat numbers in request");
    }
}
