package com.paytm.reservation.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveRequest(
        @NotEmpty List<@NotBlank @Size(max = 50) String> seats,
        @JsonProperty("idempotency_key") String idempotencyKey
) {
}
