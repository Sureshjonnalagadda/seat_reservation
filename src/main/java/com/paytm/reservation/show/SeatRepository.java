package com.paytm.reservation.show;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SeatRepository {

    private static final RowMapper<Seat> ROW_MAPPER = (rs, rowNum) -> new Seat(
            rs.getLong("id"),
            rs.getLong("show_id"),
            rs.getString("seat_number"),
            SeatStatus.valueOf(rs.getString("status"))
    );

    private final JdbcTemplate jdbcTemplate;

    public SeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Seat> findByShowIdOrderBySeatNumber(long showId) {
        return jdbcTemplate.query(
                """
                SELECT id, show_id, seat_number, status
                FROM seats
                WHERE show_id = ?
                ORDER BY seat_number
                """,
                ROW_MAPPER,
                showId
        );
    }

    public void insertAll(long showId, List<String> seatNumbers) {
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO seats (show_id, seat_number, status, version, created_at, updated_at)
                VALUES (?, ?, 'AVAILABLE', 0, ?, ?)
                """,
                seatNumbers,
                seatNumbers.size(),
                (ps, seatNumber) -> {
                    ps.setLong(1, showId);
                    ps.setString(2, seatNumber);
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.setTimestamp(4, Timestamp.from(now));
                }
        );
    }
}
