package com.paytm.reservation.show.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ShowDetailResponse(
        long id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        SeatCountsResponse counts,
        List<SeatStatusResponse> seats
) {
}
