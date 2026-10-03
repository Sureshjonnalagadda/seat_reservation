package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.GlobalExceptionHandler;
import com.paytm.reservation.common.security.AuthenticatedUser;
import com.paytm.reservation.common.security.JwtAuthenticationFilter;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUser() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "alice", Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    @Test
    void getReservationReturns200() throws Exception {
        when(reservationService.getReservation("res-1", 2L, Role.USER)).thenReturn(
                new ReservationResponse("res-1", 1L, 2L, List.of("A1"), 1000L, ReservationStatus.CONFIRMED)
        );

        mockMvc.perform(get("/reservations/res-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation_id").value("res-1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelReservationReturns200() throws Exception {
        when(reservationService.cancelReservation(eq("res-1"), eq(2L), eq(Role.USER))).thenReturn(
                new ReservationResponse("res-1", 1L, 2L, List.of("A1"), 1000L, ReservationStatus.CANCELLED)
        );

        mockMvc.perform(post("/reservations/res-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
