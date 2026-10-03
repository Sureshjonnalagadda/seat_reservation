package com.paytm.reservation.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateUserException.class)
    ResponseEntity<ApiErrorResponse> duplicateUser(DuplicateUserException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request);
    }

    @ExceptionHandler(ShowNotFoundException.class)
    ResponseEntity<ApiErrorResponse> showNotFound(ShowNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSeatNumbersException.class)
    ResponseEntity<ApiErrorResponse> duplicateSeats(DuplicateSeatNumbersException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "DUPLICATE_SEAT_NUMBERS", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        String requestId = request.getHeader("X-Request-ID");
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), code, message, requestId);
        return ResponseEntity.status(status).body(body);
    }
}
