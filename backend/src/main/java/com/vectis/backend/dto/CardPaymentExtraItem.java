package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Cargo adicional registrado junto con un pago de tarjeta")
public record CardPaymentExtraItem(
        @Schema(description = "Descripción del cargo", example = "Interés por financiación") String description,
        @Schema(description = "Monto del cargo") BigDecimal amount,
        @Schema(description = "Ícono de la categoría") String categoryIcon,
        @Schema(description = "Color de la categoría") String categoryColor
) {}
