package com.example.db_setup.service.exceptions;

import lombok.Getter;

@Getter
public class GameProgressNotFoundException extends RuntimeException {
    private final String field;

    public GameProgressNotFoundException(String field) {
        this.field = field;
    }

    public GameProgressNotFoundException() {
        this.field = "none";
    }
}
