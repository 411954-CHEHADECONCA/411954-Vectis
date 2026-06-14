package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Saldo calculado de una cuenta a una fecha determinada")
public record AccountBalanceResponse(

    @Schema(description = "ID de la cuenta", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID accountId,

    @Schema(description = "Nombre de la cuenta", example = "Cuenta Galicia")
    String name,

    @Schema(description = "Moneda de la cuenta", example = "ARS")
    String ccy,

    @Schema(description = "Saldo inicial ingresado por el usuario", example = "150000.0000")
    BigDecimal openingBalance,

    @Schema(description = "Saldo calculado: saldo inicial + suma neta de movimientos hasta asOf", example = "163500.0000")
    BigDecimal computedBalance,

    @Schema(description = "Fecha hasta la cual se calculó el saldo", example = "2025-01-31")
    LocalDate asOf
) {}
