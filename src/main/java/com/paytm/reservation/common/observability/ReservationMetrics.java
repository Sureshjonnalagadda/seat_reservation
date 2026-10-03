package com.paytm.reservation.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReservationMetrics {

    private final Counter reservationsConfirmed;
    private final Map<String, Counter> declinedByReason = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> seatsAvailableByShow = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public ReservationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.reservationsConfirmed = Counter.builder("reservations_confirmed_total")
                .description("Successful new reservations")
                .register(meterRegistry);
    }

    public void recordConfirmed() {
        reservationsConfirmed.increment();
    }

    public void recordDeclined(String reason) {
        declinedByReason.computeIfAbsent(reason, r -> Counter.builder("reservations_declined_total")
                .description("Reservation attempts declined")
                .tag("reason", r)
                .register(meterRegistry))
                .increment();
    }

    public void updateSeatsAvailable(long showId, int available) {
        AtomicInteger holder = seatsAvailableByShow.computeIfAbsent(showId, id -> {
            AtomicInteger value = new AtomicInteger(available);
            Gauge.builder("seats_available", value, AtomicInteger::get)
                    .description("Available seats for a show")
                    .tags(Tags.of("show_id", String.valueOf(id)))
                    .register(meterRegistry);
            return value;
        });
        holder.set(available);
    }
}
