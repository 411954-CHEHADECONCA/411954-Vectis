package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sección de flujo (ingresos o egresos) del cashflow")
public record CashflowFlowSection(

    @Schema(description = "Total de la sección, desglosado por moneda")
    MoneyByCcy total,

    @Schema(description = "Suma de todos los presupuestos del tipo (INCOME o EXPENSE), incluyendo categorías sin transacciones. Siempre ARS (usd=0): CategoryBudget no tiene moneda propia (limitación conocida)")
    MoneyByCcy totalBudgeted,

    @Schema(description = "Detalle por categoría, ordenado por monto (normalizado a ARS) descendente")
    List<CashflowCategoryRow> byCategory
) {}
