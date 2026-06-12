package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Resumen agregado de movimientos del período/filtros")
public record MovementSummaryResponse(

        @Schema(description = "Total de ingresos del período", example = "1740000.0000")
        BigDecimal totalIncome,

        @Schema(description = "Total de egresos del período", example = "1319850.0000")
        BigDecimal totalExpense,

        @Schema(description = "Neto (ingresos - egresos)", example = "420150.0000")
        BigDecimal net,

        @Schema(description = "Cantidad de movimientos del período", example = "13")
        long count
) {}
