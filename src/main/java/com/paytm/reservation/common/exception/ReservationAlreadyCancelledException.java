package com.paytm.reservation.common.exception;

public class ReservationAlreadyCancelledException extends RuntimeException {

    public ReservationAlreadyCancelledException() {
        super("Reservation is already cancelled");
    }
}
