package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Instrumento de renta fija disponible en DoctaCapital")
public record InstrumentDto(
        @Schema(example = "AL30")              String ticker,
        @Schema(example = "BONO SOBERANO AL30") String nombre,
        @Schema(example = "BONO")              String tipo,
        @Schema(example = "1450.5000")         BigDecimal lastPrice,
        @Schema(example = "2026-06-24")        LocalDate priceDate,
        @Schema(example = "2030-07-09")        LocalDate maturityDate,
        @Schema(description = "Moneda de cotización/pago del instrumento (null si no se pudo determinar)",
                example = "USD", nullable = true) String currency
) {}
