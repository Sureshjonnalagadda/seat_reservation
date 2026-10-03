package com.paytm.reservation.show;

import java.time.Instant;

public record Show(
        long id,
        String name,
        long pricePaise,
        int perUserLimit,
        int totalSeats,
        Instant createdAt,
        Instant updatedAt
) {
}
