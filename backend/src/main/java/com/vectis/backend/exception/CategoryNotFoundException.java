package com.vectis.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CategoryNotFoundException extends VectisException {

    public CategoryNotFoundException(UUID id) {
        super("Category not found: " + id, HttpStatus.NOT_FOUND);
    }
}
