package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvestmentNotFoundException extends VectisException {

    public InvestmentNotFoundException(UUID id) {
        super("Activo de inversión no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
