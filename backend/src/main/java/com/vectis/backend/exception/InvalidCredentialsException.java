package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends VectisException {

    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }
}
