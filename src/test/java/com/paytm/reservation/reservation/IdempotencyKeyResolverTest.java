package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.IdempotencyKeyConflictException;
import com.paytm.reservation.common.exception.MissingIdempotencyKeyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyResolverTest {

    @Test
    void prefersHeaderWhenBothMatch() {
        assertThat(IdempotencyKeyResolver.resolve("key-a", "key-a")).isEqualTo("key-a");
    }

    @Test
    void usesBodyWhenHeaderMissing() {
        assertThat(IdempotencyKeyResolver.resolve(null, "body-key")).isEqualTo("body-key");
    }

    @Test
    void rejectsWhenBothMissing() {
        assertThatThrownBy(() -> IdempotencyKeyResolver.resolve(null, null))
                .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    @Test
    void rejectsWhenHeaderAndBodyDiffer() {
        assertThatThrownBy(() -> IdempotencyKeyResolver.resolve("a", "b"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }
}
