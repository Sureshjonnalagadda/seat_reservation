package com.paytm.reservation.common.security;

import com.paytm.reservation.user.Role;
import com.paytm.reservation.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getExpirationSeconds());
        return Jwts.builder()
                .subject(user.externalUserId())
                .claim("role", user.role().name())
                .claim("uid", user.id())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        String roleName = claims.get("role", String.class);
        Number uid = claims.get("uid", Number.class);
        if (subject == null || roleName == null || uid == null) {
            throw new IllegalArgumentException("Invalid token claims");
        }
        return new AuthenticatedUser(uid.longValue(), subject, Role.valueOf(roleName));
    }

    public long getExpirationSeconds() {
        return jwtProperties.getExpirationSeconds();
    }
}
