package com.paytm.reservation.reservation;

import com.paytm.reservation.common.security.AuthenticatedUser;
import com.paytm.reservation.common.security.SecurityUtils;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{id}")
    public ReservationResponse getReservation(@PathVariable("id") String reservationId) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        return reservationService.getReservation(reservationId, user.getUserId(), user.getRole());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public ReservationResponse cancelReservation(@PathVariable("id") String reservationId) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        return reservationService.cancelReservation(reservationId, user.getUserId(), user.getRole());
    }
}
