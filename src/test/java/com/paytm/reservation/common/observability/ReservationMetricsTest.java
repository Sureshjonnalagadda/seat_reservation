package com.paytm.reservation.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationMetricsTest {

    @Test
    void recordsConfirmedAndDeclinedCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReservationMetrics metrics = new ReservationMetrics(registry);

        metrics.recordConfirmed();
        metrics.recordDeclined("seat_taken");
        metrics.updateSeatsAvailable(1L, 10);

        assertThat(registry.find("reservations_confirmed_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("reservations_declined_total").tag("reason", "seat_taken").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("seats_available").tag("show_id", "1").gauge().value()).isEqualTo(10.0);
    }
}
