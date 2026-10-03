package com.paytm.reservation.show;

import com.paytm.reservation.common.exception.DuplicateSeatNumbersException;
import com.paytm.reservation.common.exception.ShowNotFoundException;
import com.paytm.reservation.show.dto.CreateShowRequest;
import com.paytm.reservation.show.dto.CreateShowResponse;
import com.paytm.reservation.show.dto.ShowDetailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {

    @Mock
    private ShowRepository showRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ShowService showService;

    @Test
    void createShowInsertsShowAndSeats() {
        CreateShowRequest request = new CreateShowRequest(
                "Concert",
                List.of("A1", "A2"),
                150000L,
                4
        );
        Show show = new Show(1L, "Concert", 150000L, 4, 2, Instant.now(), Instant.now());
        when(showRepository.insert("Concert", 150000L, 4, 2)).thenReturn(show);

        CreateShowResponse response = showService.createShow(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.totalSeats()).isEqualTo(2);
        verify(seatRepository).insertAll(eq(1L), eq(List.of("A1", "A2")));
    }

    @Test
    void createShowRejectsDuplicateSeatNumbers() {
        CreateShowRequest request = new CreateShowRequest(
                "Concert",
                List.of("A1", "A1"),
                100L,
                2
        );

        assertThatThrownBy(() -> showService.createShow(request))
                .isInstanceOf(DuplicateSeatNumbersException.class);
    }

    @Test
    void getShowReturnsCountsFromSeats() {
        Show show = new Show(1L, "Concert", 150000L, 4, 2, Instant.now(), Instant.now());
        when(showRepository.findById(1L)).thenReturn(Optional.of(show));
        when(seatRepository.findByShowIdOrderBySeatNumber(1L)).thenReturn(List.of(
                new Seat(10L, 1L, "A1", SeatStatus.CONFIRMED),
                new Seat(11L, 1L, "A2", SeatStatus.AVAILABLE)
        ));

        ShowDetailResponse response = showService.getShow(1L);

        assertThat(response.counts().available()).isEqualTo(1);
        assertThat(response.counts().confirmed()).isEqualTo(1);
        assertThat(response.seats()).hasSize(2);
    }

    @Test
    void getShowThrowsWhenMissing() {
        when(showRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> showService.getShow(99L))
                .isInstanceOf(ShowNotFoundException.class);
    }
}
