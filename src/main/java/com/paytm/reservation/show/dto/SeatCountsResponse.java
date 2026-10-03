package com.paytm.reservation.show.dto;

public record SeatCountsResponse(
        int available,
        int held,
        int confirmed
) {
}
