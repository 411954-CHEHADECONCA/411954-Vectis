package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvestmentPaymentNotFoundException extends VectisException {

    public InvestmentPaymentNotFoundException(UUID id) {
        super("Pago del calendario no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
