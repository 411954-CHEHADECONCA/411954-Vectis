package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Sección de inversiones del cashflow")
public record CashflowInvestmentSection(

    @Schema(description = "Total invertido en el período, desglosado por moneda")
    MoneyByCcy total,

    @Schema(description = "Porcentaje sobre el saldo pre-inversión, calculado sobre montos normalizados a ARS con la cotización OFICIAL del período", example = "10.87", nullable = true)
    BigDecimal pctOfPreBalance,

    @Schema(description = "Detalle por instrumento")
    List<CashflowInvestmentRow> instruments
) {}
