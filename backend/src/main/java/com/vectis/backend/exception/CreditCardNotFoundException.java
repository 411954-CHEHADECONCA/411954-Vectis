package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CreditCardNotFoundException extends VectisException {
    public CreditCardNotFoundException(UUID id) {
        super("Tarjeta no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
