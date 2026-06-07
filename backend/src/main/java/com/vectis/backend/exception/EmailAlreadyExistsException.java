package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends VectisException {

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email, HttpStatus.CONFLICT);
    }
}
