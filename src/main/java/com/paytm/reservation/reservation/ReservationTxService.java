package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.InvalidSeatSelectionException;
import com.paytm.reservation.common.exception.PerUserLimitExceededException;
import com.paytm.reservation.common.exception.SeatTakenException;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.show.Seat;
import com.paytm.reservation.show.SeatRepository;
import com.paytm.reservation.show.SeatStatus;
import com.paytm.reservation.show.Show;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class ReservationTxService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ShowUserBookingRepository showUserBookingRepository;

    ReservationTxService(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ShowUserBookingRepository showUserBookingRepository
    ) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.showUserBookingRepository = showUserBookingRepository;
    }

    @Transactional
    ReserveResult executeReserve(
            Show show,
            long userId,
            List<String> seats,
            String idempotencyKey,
            String requestHash
    ) {
        long showId = show.id();
        Optional<Reservation> existing = reservationRepository.findByIdempotencyForUpdate(showId, userId, idempotencyKey);
        if (existing.isPresent()) {
            return toReplayResult(existing.get(), requestHash);
        }

        showUserBookingRepository.ensureExists(showId, userId);
        int activeSeatCount = showUserBookingRepository.lockAndGetActiveSeatCount(showId, userId);
        if (activeSeatCount + seats.size() > show.perUserLimit()) {
            throw new PerUserLimitExceededException();
        }

        List<Seat> lockedSeats = seatRepository.lockSeatsForUpdate(showId, seats);
        if (lockedSeats.size() != seats.size()) {
            throw new InvalidSeatSelectionException();
        }
        for (Seat seat : lockedSeats) {
            if (seat.status() != SeatStatus.AVAILABLE) {
                throw new SeatTakenException();
            }
        }

        long amountPaise = Math.multiplyExact(show.pricePaise(), seats.size());
        String reservationId = UUID.randomUUID().toString();
        List<Long> seatIds = lockedSeats.stream().map(Seat::id).toList();

        seatRepository.confirmSeats(seatIds);
        reservationRepository.insert(reservationId, showId, userId, amountPaise, idempotencyKey, requestHash);
        reservationRepository.insertReservationSeats(reservationId, seatIds);
        showUserBookingRepository.adjustActiveSeatCount(showId, userId, seats.size());

        ReservationResponse response = new ReservationResponse(
                reservationId,
                showId,
                userId,
                seats,
                amountPaise,
                ReservationStatus.CONFIRMED
        );
        return new ReserveResult(response, false);
    }

    @Transactional
    ReservationResponse executeCancel(Reservation reservation) {
        showUserBookingRepository.ensureExists(reservation.showId(), reservation.userId());
        showUserBookingRepository.lockAndGetActiveSeatCount(reservation.showId(), reservation.userId());

        List<Seat> seats = seatRepository.lockSeatsForReservationForUpdate(reservation.id());
        List<Long> seatIds = seats.stream().map(Seat::id).toList();
        seatRepository.releaseSeats(seatIds);
        reservationRepository.deleteReservationSeats(reservation.id());
        showUserBookingRepository.adjustActiveSeatCount(reservation.showId(), reservation.userId(), -seats.size());
        reservationRepository.markCancelled(reservation.id());

        List<String> seatNumbers = seats.stream().map(Seat::seatNumber).sorted().toList();
        return new ReservationResponse(
                reservation.id(),
                reservation.showId(),
                reservation.userId(),
                seatNumbers,
                reservation.amountPaise(),
                ReservationStatus.CANCELLED
        );
    }

    private ReserveResult toReplayResult(Reservation existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new com.paytm.reservation.common.exception.IdempotencyKeyReusedException();
        }
        List<String> seatNumbers = reservationRepository.findSeatNumbersByReservationId(existing.id());
        ReservationResponse response = new ReservationResponse(
                existing.id(),
                existing.showId(),
                existing.userId(),
                seatNumbers,
                existing.amountPaise(),
                existing.status()
        );
        return new ReserveResult(response, true);
    }
}
