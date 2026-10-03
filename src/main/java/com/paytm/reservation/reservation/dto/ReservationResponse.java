package com.paytm.reservation.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.reservation.reservation.ReservationStatus;

import java.util.List;

public record ReservationResponse(
        @JsonProperty("reservation_id") String reservationId,
        @JsonProperty("show_id") long showId,
        @JsonProperty("user_id") long userId,
        List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        ReservationStatus status
) {
}
