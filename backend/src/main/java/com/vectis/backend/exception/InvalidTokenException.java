package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends VectisException {

    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
