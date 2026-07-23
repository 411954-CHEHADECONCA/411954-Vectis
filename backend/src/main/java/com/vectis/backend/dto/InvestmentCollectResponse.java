package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Resultado de cobrar (liquidar) un activo de inversión")
public record InvestmentCollectResponse(

    @Schema(description = "ID del activo cobrado", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID investmentId,

    @Schema(description = "Monto total acreditado (capital + rendimiento)", example = "1050000.0000")
    BigDecimal amount,

    @Schema(description = "Moneda del monto acreditado", example = "ARS")
    String currency,

    @Schema(description = "Indica si se generó alguna transacción de ingreso en la cuenta vinculada "
            + "(false si la inversión no tenía cuenta vinculada, estaba excluida del cashflow, o ambos montos eran cero)",
            example = "true")
    boolean transactionCreated,

    @Schema(description = "Capital acreditado", example = "1000000.0000")
    BigDecimal capital,

    @Schema(description = "Rendimiento acreditado", example = "50000.0000")
    BigDecimal rendimiento,

    @Schema(description = "Fecha de cobro elegida", example = "2026-07-05")
    LocalDate collectDate,

    @Schema(description = "Estado del activo tras el cobro", example = "COBRADA")
    String status
) {}
