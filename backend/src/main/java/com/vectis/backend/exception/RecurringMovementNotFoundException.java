package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class RecurringMovementNotFoundException extends VectisException {

    public RecurringMovementNotFoundException(UUID id) {
        super("Movimiento recurrente no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
