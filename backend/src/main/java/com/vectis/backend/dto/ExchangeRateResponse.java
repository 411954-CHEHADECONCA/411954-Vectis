package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cotizacion del tipo de cambio")
public record ExchangeRateResponse(
        @Schema(description = "Tipo de cotizacion", example = "MEP") String rateType,
        @Schema(description = "Precio de compra (NUMERIC exacto, sin perdida de precision)", example = "1234.5600") String buy,
        @Schema(description = "Precio de venta (NUMERIC exacto, sin perdida de precision)", example = "1236.0000") String sell,
        @Schema(description = "Fecha de la cotizacion (ISO-8601)", example = "2026-06-22") String rateDate,
        @Schema(description = "Fuente de datos", example = "argentinadatos.com") String source
) {}
