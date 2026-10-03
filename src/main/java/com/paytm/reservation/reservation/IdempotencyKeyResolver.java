package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.IdempotencyKeyConflictException;
import com.paytm.reservation.common.exception.MissingIdempotencyKeyException;

public final class IdempotencyKeyResolver {

    private IdempotencyKeyResolver() {
    }

    public static String resolve(String headerKey, String bodyKey) {
        boolean hasHeader = headerKey != null && !headerKey.isBlank();
        boolean hasBody = bodyKey != null && !bodyKey.isBlank();
        if (!hasHeader && !hasBody) {
            throw new MissingIdempotencyKeyException();
        }
        if (hasHeader && hasBody && !headerKey.trim().equals(bodyKey.trim())) {
            throw new IdempotencyKeyConflictException();
        }
        return hasHeader ? headerKey.trim() : bodyKey.trim();
    }
}
