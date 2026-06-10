package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AccountNotFoundException extends VectisException {

    public AccountNotFoundException(UUID id) {
        super("Cuenta no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
