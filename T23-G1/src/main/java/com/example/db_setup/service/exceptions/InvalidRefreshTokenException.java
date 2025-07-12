package com.example.db_setup.service.exceptions;

import lombok.Getter;

@Getter
public class InvalidRefreshTokenException extends RuntimeException {
    private final String field;

    public InvalidRefreshTokenException(String field) {
        this.field = field;
    }

    public InvalidRefreshTokenException() {
        this.field = "none";
    }
}
