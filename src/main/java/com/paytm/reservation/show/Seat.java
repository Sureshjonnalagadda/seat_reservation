package com.paytm.reservation.show;

public record Seat(
        long id,
        long showId,
        String seatNumber,
        SeatStatus status
) {
}
