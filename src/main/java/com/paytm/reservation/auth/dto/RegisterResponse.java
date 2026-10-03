package com.paytm.reservation.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.reservation.user.Role;

public record RegisterResponse(
        @JsonProperty("user_id") long userId,
        String username,
        Role role
) {
}
