package com.paytm.reservation.show.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateShowRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<@NotBlank @Size(max = 50) String> seats,
        @JsonProperty("price_paise") @Positive long pricePaise,
        @JsonProperty("per_user_limit") @Positive int perUserLimit
) {
}
