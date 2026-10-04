package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.IdempotencyKeyReusedException;
import com.paytm.reservation.common.exception.PerUserLimitExceededException;
import com.paytm.reservation.common.exception.ReservationAccessDeniedException;
import com.paytm.reservation.common.exception.ReservationAlreadyCancelledException;
import com.paytm.reservation.common.exception.ReservationNotFoundException;
import com.paytm.reservation.common.exception.SeatTakenException;
import com.paytm.reservation.common.exception.ShowNotFoundException;
import com.paytm.reservation.common.observability.ReservationEventLogger;
import com.paytm.reservation.common.observability.ReservationMetrics;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.show.SeatRepository;
import com.paytm.reservation.show.Show;
import com.paytm.reservation.show.ShowRepository;
import com.paytm.reservation.user.Role;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationTxService reservationTxService;
    private final ReservationMetrics reservationMetrics;
    private final ReservationEventLogger reservationEventLogger;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationTxService reservationTxService,
            ReservationMetrics reservationMetrics,
            ReservationEventLogger reservationEventLogger
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationTxService = reservationTxService;
        this.reservationMetrics = reservationMetrics;
        this.reservationEventLogger = reservationEventLogger;
    }

    public ReserveResult reserve(
            long showId,
            long userId,
            List<String> seats,
            String headerIdempotencyKey,
            String bodyIdempotencyKey
    ) {
        String idempotencyKey = IdempotencyKeyResolver.resolve(headerIdempotencyKey, bodyIdempotencyKey);
        List<String> normalizedSeats = SeatSelectionNormalizer.normalizeAndSort(seats);
        String requestHash = RequestHashUtil.hash(showId, userId, normalizedSeats);
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        reservationEventLogger.reservationAttempt(showId, userId, normalizedSeats);

        try {
            ReserveResult result = reservationTxService.executeReserve(
                    show,
                    userId,
                    normalizedSeats,
                    idempotencyKey,
                    requestHash
            );
            publishReserveOutcome(showId, userId, result);
            refreshSeatsAvailableGauge(showId);
            return result;
        } catch (DuplicateKeyException ex) {
            try {
                ReserveResult result = resolveDuplicateKeyRace(showId, userId, idempotencyKey, requestHash);
                publishReserveOutcome(showId, userId, result);
                refreshSeatsAvailableGauge(showId);
                return result;
            } catch (IdempotencyKeyReusedException reused) {
                reservationMetrics.recordDeclined("idempotent_replay");
                reservationEventLogger.idempotencyConflict(showId, userId, normalizedSeats);
                throw reused;
            }
        } catch (CannotAcquireLockException ex) {
            reservationMetrics.recordDeclined("seat_taken");
            reservationEventLogger.reservationDeclined(showId, userId, normalizedSeats, "SEAT_CONFLICT");
            throw new SeatTakenException();
        } catch (SeatTakenException ex) {
            reservationMetrics.recordDeclined("seat_taken");
            reservationEventLogger.reservationDeclined(showId, userId, normalizedSeats, "SEAT_CONFLICT");
            throw ex;
        } catch (PerUserLimitExceededException ex) {
            reservationMetrics.recordDeclined("per_user_limit");
            reservationEventLogger.reservationDeclined(showId, userId, normalizedSeats, "PER_USER_LIMIT_CONFLICT");
            throw ex;
        } catch (IdempotencyKeyReusedException ex) {
            reservationMetrics.recordDeclined("idempotent_replay");
            reservationEventLogger.idempotencyConflict(showId, userId, normalizedSeats);
            throw ex;
        }
    }

    public ReservationResponse getReservation(String reservationId, long userId, Role role) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (role != Role.ADMIN && reservation.userId() != userId) {
            throw new ReservationAccessDeniedException();
        }
        List<String> seatNumbers = reservationRepository.findSeatNumbersByReservationId(reservationId);
        return new ReservationResponse(
                reservation.id(),
                reservation.showId(),
                reservation.userId(),
                seatNumbers,
                reservation.amountPaise(),
                reservation.status()
        );
    }

    @Transactional
    public ReservationResponse cancelReservation(String reservationId, long userId, Role role) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (role != Role.ADMIN && reservation.userId() != userId) {
            throw new ReservationAccessDeniedException();
        }
        if (reservation.status() == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException();
        }
        ReservationResponse response = reservationTxService.executeCancel(reservation);
        reservationEventLogger.reservationCancelled(
                response.showId(),
                response.userId(),
                response.reservationId(),
                response.seats()
        );
        refreshSeatsAvailableGauge(response.showId());
        return response;
    }

    private void publishReserveOutcome(long showId, long userId, ReserveResult result) {
        ReservationResponse response = result.response();
        if (result.idempotentReplay()) {
            reservationEventLogger.idempotentReplay(
                    showId,
                    userId,
                    response.reservationId(),
                    response.seats()
            );
            return;
        }
        reservationMetrics.recordConfirmed();
        reservationEventLogger.reservationConfirmed(
                showId,
                userId,
                response.reservationId(),
                response.seats()
        );
    }

    private ReserveResult resolveDuplicateKeyRace(
            long showId,
            long userId,
            String idempotencyKey,
            String requestHash
    ) {
        Reservation existing = reservationRepository.findByIdempotency(showId, userId, idempotencyKey)
                .orElseThrow(() -> new DuplicateKeyException("Idempotency race without reservation row"));
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
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

    private void refreshSeatsAvailableGauge(long showId) {
        reservationMetrics.updateSeatsAvailable(showId, seatRepository.countAvailableSeats(showId));
    }
}
