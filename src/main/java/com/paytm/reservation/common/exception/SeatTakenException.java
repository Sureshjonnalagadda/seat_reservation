package com.paytm.reservation.common.exception;

public class SeatTakenException extends RuntimeException {

    public SeatTakenException() {
        super("One or more requested seats are not available");
    }
}
