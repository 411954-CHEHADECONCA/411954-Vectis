package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Schema(description = "Consumo (fila hija) dentro de una tarjeta en la matriz")
public record CardMatrixConsumo(
        @Schema(description = "Descripción del consumo", example = "Notebook Lenovo") String description,
        @Schema(description = "Etiqueta de cuota (k/N), null si es pago único", example = "3/6") String installmentLabel,
        @Schema(description = "Ícono de la categoría") String categoryIcon,
        @Schema(description = "Color de la categoría") String categoryColor,
        @Schema(description = "Montos por mes alineados a las columnas (null = celda vacía)") List<BigDecimal> cellsByMonth
) {}
