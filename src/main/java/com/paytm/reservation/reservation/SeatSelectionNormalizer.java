package com.paytm.reservation.reservation;

import com.paytm.reservation.common.exception.DuplicateSeatNumbersException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SeatSelectionNormalizer {

    private SeatSelectionNormalizer() {
    }

    public static List<String> normalizeAndSort(List<String> seats) {
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>(seats.size());
        for (String seat : seats) {
            String trimmed = seat.trim();
            if (!seen.add(trimmed)) {
                throw new DuplicateSeatNumbersException();
            }
            normalized.add(trimmed);
        }
        normalized.sort(String::compareTo);
        return normalized;
    }
}
