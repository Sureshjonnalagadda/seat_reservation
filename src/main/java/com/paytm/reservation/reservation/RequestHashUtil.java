package com.paytm.reservation.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class RequestHashUtil {

    private RequestHashUtil() {
    }

    public static String hash(long showId, long userId, List<String> sortedSeatNumbers) {
        String canonical = showId + "|" + userId + "|" + String.join(",", sortedSeatNumbers);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
