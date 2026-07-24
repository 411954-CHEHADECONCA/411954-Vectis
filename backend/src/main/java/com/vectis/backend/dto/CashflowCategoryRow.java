package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Fila de categoría en una sección de flujo (ingresos o egresos)")
public record CashflowCategoryRow(

    @Schema(description = "ID de la categoría", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String categoryId,

    @Schema(description = "Nombre de la categoría", example = "Alimentación")
    String name,

    @Schema(description = "Ícono de la categoría", example = "🛒")
    String icon,

    @Schema(description = "Color de la categoría en formato hex", example = "#22c55e")
    String color,

    @Schema(description = "Monto total de la categoría en el período, desglosado por moneda")
    MoneyByCcy amount,

    @Schema(description = "Porcentaje sobre el total de la sección, calculado sobre montos normalizados a ARS con la cotización OFICIAL del período (ver oficialRateAtPeriod)", example = "18.50")
    BigDecimal pctOfTotal,

    @Schema(description = "Monto presupuestado para la categoría (null si sin presupuesto). Los presupuestos no tienen moneda propia (limitación conocida de CategoryBudget): siempre se tratan como ARS, con usd=0", example = "{\"ars\":30000.0000,\"usd\":0.0000}", nullable = true)
    MoneyByCcy budgeted,

    @Schema(description = "Porcentaje ejecutado del presupuesto (null si sin presupuesto), calculado sobre el monto normalizado a ARS", example = "83.33", nullable = true)
    BigDecimal pctOfBudget
) {}
