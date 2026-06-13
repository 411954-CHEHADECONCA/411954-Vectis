package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Schema(description = "Cara de tarjeta con consumo y fechas del ciclo")
public record CardFaceResponse(
        @Schema(description = "ID de la tarjeta") UUID id,
        @Schema(description = "Banco", example = "Galicia") String bank,
        @Schema(description = "Red", example = "Visa") String network,
        @Schema(description = "Últimos 4 dígitos", example = "4821") String last4,
        @Schema(description = "Moneda", example = "ARS") String ccy,
        @Schema(description = "Color de acento", example = "#52eacd") String accent,
        @Schema(description = "Límite de crédito", example = "1500000.0000") BigDecimal creditLimit,
        @Schema(description = "Deuda pendiente (suma de movimientos con vencimiento >= mes actual)", example = "612300.0000") BigDecimal consumido,
        @Schema(description = "Próxima fecha de cierre", example = "2026-06-24") LocalDate nextClosingDate,
        @Schema(description = "Próxima fecha de vencimiento", example = "2026-07-02") LocalDate nextDueDate
) {}
