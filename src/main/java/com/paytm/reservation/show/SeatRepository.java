package com.paytm.reservation.show;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

    public int countAvailableSeats(long showId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'AVAILABLE'",
                Integer.class,
                showId
        );
        return count == null ? 0 : count;
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

    public List<Seat> lockSeatsForUpdate(long showId, List<String> sortedSeatNumbers) {
        if (sortedSeatNumbers.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", sortedSeatNumbers.stream().map(s -> "?").toList());
        String sql = """
                SELECT id, show_id, seat_number, status
                FROM seats
                WHERE show_id = ? AND seat_number IN (%s)
                ORDER BY seat_number
                FOR UPDATE
                """.formatted(placeholders);
        List<Object> args = new ArrayList<>(sortedSeatNumbers.size() + 1);
        args.add(showId);
        args.addAll(sortedSeatNumbers);
        return jdbcTemplate.query(sql, ROW_MAPPER, args.toArray());
    }

    public void confirmSeats(List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                UPDATE seats
                SET status = 'CONFIRMED', version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                seatIds,
                seatIds.size(),
                (ps, seatId) -> ps.setLong(1, seatId)
        );
    }

    public void releaseSeats(List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                UPDATE seats
                SET status = 'AVAILABLE', version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                seatIds,
                seatIds.size(),
                (ps, seatId) -> ps.setLong(1, seatId)
        );
    }

    public List<Seat> lockSeatsForReservationForUpdate(String reservationId) {
        return jdbcTemplate.query(
                """
                SELECT s.id, s.show_id, s.seat_number, s.status
                FROM seats s
                INNER JOIN reservation_seats rs ON rs.seat_id = s.id
                WHERE rs.reservation_id = ?
                ORDER BY s.seat_number
                FOR UPDATE
                """,
                ROW_MAPPER,
                reservationId
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
