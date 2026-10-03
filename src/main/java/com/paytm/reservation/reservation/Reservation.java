package com.paytm.reservation.reservation;

import java.time.Instant;

public record Reservation(
        String id,
        long showId,
        long userId,
        ReservationStatus status,
        long amountPaise,
        String idempotencyKey,
        String requestHash,
        Instant createdAt,
        Instant cancelledAt
) {
}
