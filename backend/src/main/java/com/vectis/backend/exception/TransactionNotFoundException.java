package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends VectisException {
    public TransactionNotFoundException(UUID id) {
        super("Movimiento no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
