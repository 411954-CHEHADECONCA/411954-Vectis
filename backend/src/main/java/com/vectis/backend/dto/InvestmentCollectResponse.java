package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Resultado de cobrar (liquidar) un activo de inversión")
public record InvestmentCollectResponse(

    @Schema(description = "ID del activo cobrado", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID investmentId,

    @Schema(description = "Monto acreditado (capital actualizado del activo al momento del cobro)", example = "1050000.0000")
    BigDecimal amount,

    @Schema(description = "Moneda del monto acreditado", example = "ARS")
    String currency,

    @Schema(description = "Indica si se generó una transacción de ingreso en la cuenta vinculada "
            + "(false si la inversión no tenía cuenta vinculada, estaba excluida del cashflow, o el monto era cero)",
            example = "true")
    boolean transactionCreated
) {}
