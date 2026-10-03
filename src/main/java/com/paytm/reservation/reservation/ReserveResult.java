package com.paytm.reservation.reservation;

import com.paytm.reservation.reservation.dto.ReservationResponse;

public record ReserveResult(ReservationResponse response, boolean idempotentReplay) {
}
