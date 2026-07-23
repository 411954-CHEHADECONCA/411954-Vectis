package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvestmentValuationNotFoundException extends VectisException {

    public InvestmentValuationNotFoundException(UUID id) {
        super("Valuación de inversión no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
