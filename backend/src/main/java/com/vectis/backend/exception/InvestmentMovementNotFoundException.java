package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvestmentMovementNotFoundException extends VectisException {

    public InvestmentMovementNotFoundException(UUID id) {
        super("Movimiento de inversión no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
