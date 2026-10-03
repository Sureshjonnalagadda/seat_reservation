package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.ReservationAccessDeniedException;
import com.paytm.reservation.common.exception.ReservationAlreadyCancelledException;
import com.paytm.reservation.common.exception.ReservationNotFoundException;
import com.paytm.reservation.common.exception.ShowNotFoundException;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.show.Show;
import com.paytm.reservation.show.ShowRepository;
import com.paytm.reservation.user.Role;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationTxService reservationTxService;

    public ReservationService(
            ShowRepository showRepository,
            ReservationRepository reservationRepository,
            ReservationTxService reservationTxService
    ) {
        this.showRepository = showRepository;
        this.reservationRepository = reservationRepository;
        this.reservationTxService = reservationTxService;
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

        try {
            return reservationTxService.executeReserve(show, userId, normalizedSeats, idempotencyKey, requestHash);
        } catch (DuplicateKeyException ex) {
            Reservation existing = reservationRepository.findByIdempotency(showId, userId, idempotencyKey)
                    .orElseThrow(() -> ex);
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
        return reservationTxService.executeCancel(reservation);
    }
}
