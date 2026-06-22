package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Saldo de una cuenta incluida en el cashflow")
public record CashflowAccountBalance(

    @Schema(description = "ID de la cuenta", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String accountId,

    @Schema(description = "Nombre de la cuenta", example = "Cuenta Galicia")
    String name,

    @Schema(description = "Moneda de la cuenta", example = "ARS")
    String ccy,

    @Schema(description = "Saldo calculado a la fecha", example = "150000.0000")
    BigDecimal balance
) {}
