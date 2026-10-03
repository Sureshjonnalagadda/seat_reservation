package com.paytm.reservation.common.exception;

public class ReservationAccessDeniedException extends RuntimeException {

    public ReservationAccessDeniedException() {
        super("You do not have access to this reservation");
    }
}
