package com.paytm.reservation.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShowUserBookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShowUserBookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureExists(long showId, long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO show_user_booking (show_id, user_id, active_seat_count, created_at, updated_at)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE updated_at = updated_at
                """,
                showId,
                userId
        );
    }

    public int lockAndGetActiveSeatCount(long showId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT active_seat_count
                FROM show_user_booking
                WHERE show_id = ? AND user_id = ?
                FOR UPDATE
                """,
                Integer.class,
                showId,
                userId
        );
        return count == null ? 0 : count;
    }

    public void adjustActiveSeatCount(long showId, long userId, int delta) {
        jdbcTemplate.update(
                """
                UPDATE show_user_booking
                SET active_seat_count = active_seat_count + ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE show_id = ? AND user_id = ?
                """,
                delta,
                showId,
                userId
        );
    }
}
