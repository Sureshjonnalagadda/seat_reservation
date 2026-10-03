package com.paytm.reservation.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIdSupport {

    private RequestIdSupport() {
    }

    public static String resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (attribute instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        String header = request.getHeader(RequestIdFilter.HEADER_NAME);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return null;
    }
}
