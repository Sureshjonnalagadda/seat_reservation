package com.paytm.reservation.common.exception;

public class InvalidSeatSelectionException extends RuntimeException {

    public InvalidSeatSelectionException() {
        super("One or more seat numbers are invalid for this show");
    }
}
