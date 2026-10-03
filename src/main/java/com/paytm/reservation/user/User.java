package com.paytm.reservation.user;

import java.time.Instant;

public record User(
        long id,
        String externalUserId,
        Role role,
        String passwordHash,
        Instant createdAt,
        Instant updatedAt
) {
}
