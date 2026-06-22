package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Sección de saldo (apertura o cierre) del cashflow")
public record CashflowBalanceSection(

    @Schema(description = "Saldo total de todas las cuentas incluidas", example = "450000.0000")
    BigDecimal total,

    @Schema(description = "Detalle por cuenta")
    List<CashflowAccountBalance> accounts
) {}
