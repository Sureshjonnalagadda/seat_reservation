package com.paytm.reservation.show.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateShowResponse(
        long id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats
) {
}
