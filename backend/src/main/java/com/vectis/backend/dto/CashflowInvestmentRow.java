package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Fila de instrumento de inversión en el cashflow")
public record CashflowInvestmentRow(

    @Schema(description = "Nombre del instrumento", example = "Plazo Fijo")
    String name,

    @Schema(description = "Ícono del instrumento", example = "📈")
    String icon,

    @Schema(description = "Color del instrumento en formato hex", example = "#6366f1")
    String color,

    @Schema(description = "Monto invertido en el período, desglosado por moneda")
    MoneyByCcy amount,

    @Schema(description = "TEA del instrumento en porcentaje (null en MVP)", example = "95.00", nullable = true)
    BigDecimal teaPct
) {}
