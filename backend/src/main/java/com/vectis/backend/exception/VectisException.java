package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

public class VectisException extends RuntimeException {

    private final HttpStatus status;

    public VectisException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
