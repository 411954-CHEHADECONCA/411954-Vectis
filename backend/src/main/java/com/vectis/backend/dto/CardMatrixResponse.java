package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Schema(description = "Matriz de proyección de deuda diferida por tarjeta y mes")
public record CardMatrixResponse(
        @Schema(description = "Encabezados de meses (columnas)", example = "[\"jun 2026\", \"jul 2026\"]") List<String> months,
        @Schema(description = "Tarjetas (filas padre)") List<CardMatrixCard> cards,
        @Schema(description = "Totales consolidados por mes (footer)") List<BigDecimal> totalsByMonth
) {}
