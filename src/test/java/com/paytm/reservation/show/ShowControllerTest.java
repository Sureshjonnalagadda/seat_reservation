package com.paytm.reservation.show;

import com.paytm.reservation.common.exception.GlobalExceptionHandler;
import com.paytm.reservation.common.security.AuthenticatedUser;
import com.paytm.reservation.common.security.JwtAuthenticationFilter;
import com.paytm.reservation.reservation.ReservationService;
import com.paytm.reservation.reservation.ReservationStatus;
import com.paytm.reservation.reservation.ReserveResult;
import com.paytm.reservation.reservation.dto.ReservationResponse;
import com.paytm.reservation.user.Role;
import com.paytm.reservation.show.dto.CreateShowResponse;
import com.paytm.reservation.show.dto.SeatCountsResponse;
import com.paytm.reservation.show.dto.SeatStatusResponse;
import com.paytm.reservation.show.dto.ShowDetailResponse;
import com.paytm.reservation.show.SeatStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShowService showService;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void createShowReturns201() throws Exception {
        when(showService.createShow(any())).thenReturn(
                new CreateShowResponse(1L, "Mumbai Concert", 150000L, 4, 4)
        );

        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mumbai Concert",
                                  "seats": ["A1","A2","A3","A4"],
                                  "price_paise": 150000,
                                  "per_user_limit": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.total_seats").value(4));
    }

    @Test
    void getShowReturnsDetail() throws Exception {
        when(showService.getShow(1L)).thenReturn(new ShowDetailResponse(
                1L,
                "Mumbai Concert",
                150000L,
                4,
                2,
                new SeatCountsResponse(1, 0, 1),
                List.of(
                        new SeatStatusResponse("A1", SeatStatus.CONFIRMED),
                        new SeatStatusResponse("A2", SeatStatus.AVAILABLE)
                )
        ));

        mockMvc.perform(get("/shows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.available").value(1))
                .andExpect(jsonPath("$.seats[0].seat_number").value("A1"));
    }

    @Test
    void reserveReturns201() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(10L, "alice", Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
        when(reservationService.reserve(anyLong(), eq(10L), any(), any(), any())).thenReturn(
                new ReserveResult(
                        new ReservationResponse("res-1", 1L, 10L, List.of("A1"), 150000L, ReservationStatus.CONFIRMED),
                        false
                )
        );

        mockMvc.perform(post("/shows/1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("{\"seats\":[\"A1\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservation_id").value("res-1"));
    }
}
