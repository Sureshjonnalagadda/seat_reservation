package com.paytm.reservation.reservation;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ReservationRepository {

    private static final RowMapper<Reservation> ROW_MAPPER = (rs, rowNum) -> new Reservation(
            rs.getString("id"),
            rs.getLong("show_id"),
            rs.getLong("user_id"),
            ReservationStatus.valueOf(rs.getString("status")),
            rs.getLong("amount_paise"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Reservation> findByIdempotencyForUpdate(long showId, long userId, String idempotencyKey) {
        try {
            Reservation reservation = jdbcTemplate.queryForObject(
                    """
                    SELECT id, show_id, user_id, status, amount_paise, idempotency_key, request_hash,
                           created_at, cancelled_at
                    FROM reservations
                    WHERE show_id = ? AND user_id = ? AND idempotency_key = ?
                    FOR UPDATE
                    """,
                    ROW_MAPPER,
                    showId,
                    userId,
                    idempotencyKey
            );
            return Optional.of(reservation);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Reservation> findByIdempotency(long showId, long userId, String idempotencyKey) {
        try {
            Reservation reservation = jdbcTemplate.queryForObject(
                    """
                    SELECT id, show_id, user_id, status, amount_paise, idempotency_key, request_hash,
                           created_at, cancelled_at
                    FROM reservations
                    WHERE show_id = ? AND user_id = ? AND idempotency_key = ?
                    """,
                    ROW_MAPPER,
                    showId,
                    userId,
                    idempotencyKey
            );
            return Optional.of(reservation);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Reservation> findById(String reservationId) {
        try {
            Reservation reservation = jdbcTemplate.queryForObject(
                    """
                    SELECT id, show_id, user_id, status, amount_paise, idempotency_key, request_hash,
                           created_at, cancelled_at
                    FROM reservations
                    WHERE id = ?
                    """,
                    ROW_MAPPER,
                    reservationId
            );
            return Optional.of(reservation);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Reservation> findByIdForUpdate(String reservationId) {
        try {
            Reservation reservation = jdbcTemplate.queryForObject(
                    """
                    SELECT id, show_id, user_id, status, amount_paise, idempotency_key, request_hash,
                           created_at, cancelled_at
                    FROM reservations
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    ROW_MAPPER,
                    reservationId
            );
            return Optional.of(reservation);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void insert(
            String id,
            long showId,
            long userId,
            long amountPaise,
            String idempotencyKey,
            String requestHash
    ) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO reservations (
                    id, show_id, user_id, status, amount_paise, idempotency_key, request_hash,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 'CONFIRMED', ?, ?, ?, ?, ?)
                """,
                id,
                showId,
                userId,
                amountPaise,
                idempotencyKey,
                requestHash,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    public void markCancelled(String reservationId) {
        jdbcTemplate.update(
                """
                UPDATE reservations
                SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                reservationId
        );
    }

    public void insertReservationSeats(String reservationId, List<Long> seatIds) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO reservation_seats (reservation_id, seat_id) VALUES (?, ?)",
                seatIds,
                seatIds.size(),
                (ps, seatId) -> {
                    ps.setString(1, reservationId);
                    ps.setLong(2, seatId);
                }
        );
    }

    public void deleteReservationSeats(String reservationId) {
        jdbcTemplate.update("DELETE FROM reservation_seats WHERE reservation_id = ?", reservationId);
    }

    public List<String> findSeatNumbersByReservationId(String reservationId) {
        return jdbcTemplate.query(
                """
                SELECT s.seat_number
                FROM reservation_seats rs
                INNER JOIN seats s ON s.id = rs.seat_id
                WHERE rs.reservation_id = ?
                ORDER BY s.seat_number
                """,
                (rs, rowNum) -> rs.getString("seat_number"),
                reservationId
        );
    }
}
