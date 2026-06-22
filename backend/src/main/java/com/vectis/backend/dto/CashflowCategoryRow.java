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

    @Schema(description = "Monto total de la categoría en el período", example = "25000.0000")
    BigDecimal amount,

    @Schema(description = "Porcentaje sobre el total de la sección", example = "18.50")
    BigDecimal pctOfTotal,

    @Schema(description = "Monto presupuestado para la categoría (null si sin presupuesto)", example = "30000.0000", nullable = true)
    BigDecimal budgeted,

    @Schema(description = "Porcentaje ejecutado del presupuesto (null si sin presupuesto)", example = "83.33", nullable = true)
    BigDecimal pctOfBudget
) {}
