package com.paytm.reservation.show.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.reservation.show.SeatStatus;

public record SeatStatusResponse(
        @JsonProperty("seat_number") String seatNumber,
        SeatStatus status
) {
}
