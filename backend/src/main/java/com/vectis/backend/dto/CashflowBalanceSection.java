package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sección de saldo (apertura o cierre) del cashflow")
public record CashflowBalanceSection(

    @Schema(description = "Saldo total de todas las cuentas incluidas, desglosado por moneda")
    MoneyByCcy total,

    @Schema(description = "Detalle por cuenta (cada cuenta ya lleva su propia moneda en `ccy`)")
    List<CashflowAccountBalance> accounts
) {}
