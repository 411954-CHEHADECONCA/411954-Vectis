package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Movimiento de FCI registrado (suscripción o rescate)")
public record InvestmentMovementResponse(

    @Schema(description = "ID único del movimiento")
    UUID id,

    @Schema(description = "Fecha del movimiento", example = "2026-03-15")
    LocalDate movementDate,

    @Schema(description = "Tipo: SUSCRIPCION o RESCATE", example = "SUSCRIPCION")
    String type,

    @Schema(description = "Monto del movimiento", example = "500000.0000")
    BigDecimal amount,

    @Schema(description = "Cuotapartes involucradas (null para tipos que no sean FCI_CUOTAPARTES)",
            example = "400.000000")
    BigDecimal units,

    @Schema(description = "Fecha de creación del registro")
    OffsetDateTime createdAt
) {}
