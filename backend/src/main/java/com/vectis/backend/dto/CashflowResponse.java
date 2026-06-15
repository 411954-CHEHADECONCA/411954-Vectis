package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado de flujo de caja mensual del usuario")
public record CashflowResponse(

    @Schema(description = "Año del período", example = "2025")
    int year,

    @Schema(description = "Mes del período (1-12)", example = "6")
    int month,

    @Schema(description = "Etiqueta larga del período", example = "Junio 2025")
    String periodLabel,

    @Schema(description = "Abreviatura del mes", example = "jun")
    String monthShort,

    @Schema(description = "Estado del período", example = "curso", allowableValues = {"cerrado", "curso", "proyectado"})
    String status,

    @Schema(description = "Indica si los datos son una proyección basada en promedio histórico", example = "false")
    boolean isProjection,

    @Schema(description = "Saldo de apertura (último día del mes anterior)")
    CashflowBalanceSection openingBalance,

    @Schema(description = "Ingresos del período agrupados por categoría")
    CashflowFlowSection income,

    @Schema(description = "Egresos del período agrupados por categoría")
    CashflowFlowSection expenses,

    @Schema(description = "Subtotal previo a inversiones")
    CashflowSubtotal preInvestmentBalance,

    @Schema(description = "Inversiones del período")
    CashflowInvestmentSection investments,

    @Schema(description = "Saldo de cierre (último día del mes o hoy si es el mes actual)")
    CashflowBalanceSection closingBalance
) {}
