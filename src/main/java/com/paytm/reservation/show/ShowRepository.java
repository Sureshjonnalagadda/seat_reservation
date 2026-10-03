package com.paytm.reservation.show;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class ShowRepository {

    private static final RowMapper<Show> ROW_MAPPER = (rs, rowNum) -> new Show(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getLong("price_paise"),
            rs.getInt("per_user_limit"),
            rs.getInt("total_seats"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public ShowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Show> findById(long id) {
        try {
            Show show = jdbcTemplate.queryForObject(
                    """
                    SELECT id, name, price_paise, per_user_limit, total_seats, created_at, updated_at
                    FROM shows
                    WHERE id = ?
                    """,
                    ROW_MAPPER,
                    id
            );
            return Optional.of(show);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Show insert(String name, long pricePaise, int perUserLimit, int totalSeats) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO shows (name, price_paise, per_user_limit, total_seats, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setLong(2, pricePaise);
            ps.setInt(3, perUserLimit);
            ps.setInt(4, totalSeats);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert show");
        }
        return new Show(key.longValue(), name, pricePaise, perUserLimit, totalSeats, now, now);
    }
}
