package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Subtotal previo a inversiones: saldo + resultado operativo")
public record CashflowSubtotal(

    @Schema(description = "Saldo pre-inversión (apertura + resultado operativo)", example = "460000.0000")
    BigDecimal balance,

    @Schema(description = "Resultado operativo del período (ingresos - egresos)", example = "10000.0000")
    BigDecimal operativeResult,

    @Schema(description = "Tasa de ahorro como porcentaje de los ingresos", example = "7.41")
    BigDecimal savingRatePct
) {}
