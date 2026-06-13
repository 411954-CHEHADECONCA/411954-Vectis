package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Schema(description = "Próximo vencimiento de una tarjeta")
public record CardDueResponse(
        @Schema(description = "ID de la tarjeta") UUID cardId,
        @Schema(description = "Nombre de la tarjeta", example = "Galicia ····4821") String cardName,
        @Schema(description = "Fecha de vencimiento", example = "2026-07-02") LocalDate dueDate,
        @Schema(description = "Monto a pagar en ese vencimiento", example = "612300.0000") BigDecimal amount
) {}
