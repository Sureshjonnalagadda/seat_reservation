package com.paytm.reservation.show;

import com.paytm.reservation.common.security.AuthenticatedUser;
import com.paytm.reservation.common.security.SecurityUtils;
import com.paytm.reservation.reservation.ReservationService;
import com.paytm.reservation.reservation.ReserveResult;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.reservation.dto.ReserveRequest;
import com.paytm.reservation.show.dto.CreateShowRequest;
import com.paytm.reservation.show.dto.CreateShowResponse;
import com.paytm.reservation.show.dto.ShowDetailResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;
    private final ReservationService reservationService;

    public ShowController(ShowService showService, ReservationService reservationService) {
        this.showService = showService;
        this.reservationService = reservationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return showService.createShow(request);
    }

    @GetMapping("/{id}")
    public ShowDetailResponse getShow(@PathVariable("id") long showId) {
        return showService.getShow(showId);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable("id") long showId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody ReserveRequest request
    ) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        ReserveResult result = reservationService.reserve(
                showId,
                user.getUserId(),
                request.seats(),
                idempotencyHeader,
                request.idempotencyKey()
        );
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (result.idempotentReplay()) {
            builder.header("X-Idempotent-Replay", "true");
        }
        return builder.body(result.response());
    }
}
