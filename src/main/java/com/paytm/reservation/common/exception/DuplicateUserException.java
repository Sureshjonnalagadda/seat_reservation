package com.paytm.reservation.common.exception;

public class DuplicateUserException extends RuntimeException {

    private final String username;

    public DuplicateUserException(String username) {
        super("User already exists: " + username);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
