package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Subtotal previo a inversiones: saldo + resultado operativo")
public record CashflowSubtotal(

    @Schema(description = "Saldo pre-inversión (apertura + resultado operativo), desglosado por moneda")
    MoneyByCcy balance,

    @Schema(description = "Resultado operativo del período (ingresos - egresos), desglosado por moneda")
    MoneyByCcy operativeResult,

    @Schema(description = "Tasa de ahorro como porcentaje de los ingresos, calculada sobre montos normalizados a ARS con la cotización OFICIAL del período", example = "7.41")
    BigDecimal savingRatePct
) {}
