package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Sección de flujo (ingresos o egresos) del cashflow")
public record CashflowFlowSection(

    @Schema(description = "Total de la sección", example = "135000.0000")
    BigDecimal total,

    @Schema(description = "Detalle por categoría ordenado por monto descendente")
    List<CashflowCategoryRow> byCategory
) {}
