package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Precio de un instrumento de renta fija para una fecha específica")
public record InstrumentPriceResponse(
        @Schema(description = "Ticker del instrumento",        example = "AL30")         String ticker,
        @Schema(description = "Tipo de instrumento",           example = "BONO")         String type,
        @Schema(description = "Fecha de cotización",           example = "2026-06-25")   String fecha,
        @Schema(description = "Precio por unidad normalizado", example = "963.0000")     BigDecimal pricePerUnit
) {}
