package com.paytm.reservation.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventLogger.class);

    public void reservationAttempt(long showId, long userId, List<String> seatNumbers) {
        withContext(showId, userId, null, seatNumbers, "RESERVATION_ATTEMPT", () ->
                log.info("Reservation attempt"));
    }

    public void reservationConfirmed(long showId, long userId, String reservationId, List<String> seatNumbers) {
        withContext(showId, userId, reservationId, seatNumbers, "RESERVATION_CONFIRMED", () ->
                log.info("Reservation confirmed"));
    }

    public void reservationDeclined(long showId, long userId, List<String> seatNumbers, String event) {
        withContext(showId, userId, null, seatNumbers, event, () ->
                log.info("Reservation declined"));
    }

    public void idempotentReplay(long showId, long userId, String reservationId, List<String> seatNumbers) {
        withContext(showId, userId, reservationId, seatNumbers, "IDEMPOTENT_REPLAY", () ->
                log.info("Idempotent replay"));
    }

    public void idempotencyConflict(long showId, long userId, List<String> seatNumbers) {
        withContext(showId, userId, null, seatNumbers, "IDEMPOTENCY_CONFLICT", () ->
                log.info("Idempotency conflict"));
    }

    public void reservationCancelled(long showId, long userId, String reservationId, List<String> seatNumbers) {
        withContext(showId, userId, reservationId, seatNumbers, "RESERVATION_CANCELLED", () ->
                log.info("Reservation cancelled"));
    }

    private static void withContext(
            long showId,
            Long userId,
            String reservationId,
            List<String> seatNumbers,
            String event,
            Runnable action
    ) {
        putIfAbsent("show_id", String.valueOf(showId));
        if (userId != null) {
            putIfAbsent("user_id", String.valueOf(userId));
        }
        if (reservationId != null) {
            putIfAbsent("reservation_id", reservationId);
        }
        if (seatNumbers != null && !seatNumbers.isEmpty()) {
            putIfAbsent("seat_numbers", String.join(",", seatNumbers));
        }
        putIfAbsent("event", event);
        try {
            action.run();
        } finally {
            MDC.remove("show_id");
            MDC.remove("user_id");
            MDC.remove("reservation_id");
            MDC.remove("seat_numbers");
            MDC.remove("event");
        }
    }

    private static void putIfAbsent(String key, String value) {
        if (MDC.get(key) == null) {
            MDC.put(key, value);
        }
    }
}
