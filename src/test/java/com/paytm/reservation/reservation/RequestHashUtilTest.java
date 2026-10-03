package com.paytm.reservation.reservation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHashUtilTest {

    @Test
    void hashIsDeterministicForSortedSeats() {
        String first = RequestHashUtil.hash(1L, 10L, List.of("A12", "A13"));
        String second = RequestHashUtil.hash(1L, 10L, List.of("A12", "A13"));
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void hashChangesWhenSeatsChange() {
        String first = RequestHashUtil.hash(1L, 10L, List.of("A12"));
        String second = RequestHashUtil.hash(1L, 10L, List.of("A13"));
        assertThat(first).isNotEqualTo(second);
    }
}
