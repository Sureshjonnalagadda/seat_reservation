package com.paytm.reservation.show;

import com.paytm.reservation.common.exception.DuplicateSeatNumbersException;
import com.paytm.reservation.common.exception.ShowNotFoundException;
import com.paytm.reservation.common.observability.ReservationMetrics;
import com.paytm.reservation.show.dto.CreateShowRequest;
import com.paytm.reservation.show.dto.CreateShowResponse;
import com.paytm.reservation.show.dto.SeatCountsResponse;
import com.paytm.reservation.show.dto.SeatStatusResponse;
import com.paytm.reservation.show.dto.ShowDetailResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationMetrics reservationMetrics;

    public ShowService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationMetrics reservationMetrics
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public CreateShowResponse createShow(CreateShowRequest request) {
        List<String> seatNumbers = normalizeSeatNumbers(request.seats());
        Show show = showRepository.insert(
                request.name().trim(),
                request.pricePaise(),
                request.perUserLimit(),
                seatNumbers.size()
        );
        seatRepository.insertAll(show.id(), seatNumbers);
        reservationMetrics.updateSeatsAvailable(show.id(), seatNumbers.size());
        return new CreateShowResponse(
                show.id(),
                show.name(),
                show.pricePaise(),
                show.perUserLimit(),
                show.totalSeats()
        );
    }

    public ShowDetailResponse getShow(long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));
        List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumber(showId);
        int available = 0;
        int held = 0;
        int confirmed = 0;
        List<SeatStatusResponse> seatResponses = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            switch (seat.status()) {
                case AVAILABLE -> available++;
                case HELD -> held++;
                case CONFIRMED -> confirmed++;
            }
            seatResponses.add(new SeatStatusResponse(seat.seatNumber(), seat.status()));
        }
        reservationMetrics.updateSeatsAvailable(showId, available);
        return new ShowDetailResponse(
                show.id(),
                show.name(),
                show.pricePaise(),
                show.perUserLimit(),
                show.totalSeats(),
                new SeatCountsResponse(available, held, confirmed),
                seatResponses
        );
    }

    private static List<String> normalizeSeatNumbers(List<String> seats) {
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>(seats.size());
        for (String seat : seats) {
            String trimmed = seat.trim();
            if (!seen.add(trimmed)) {
                throw new DuplicateSeatNumbersException();
            }
            normalized.add(trimmed);
        }
        return normalized;
    }
}
