package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Tarjeta (fila padre) con sus consumos y subtotales por mes")
public record CardMatrixCard(
        @Schema(description = "ID de la tarjeta") UUID cardId,
        @Schema(description = "Nombre de la tarjeta", example = "Galicia ····4821") String cardName,
        @Schema(description = "Color de acento") String accent,
        @Schema(description = "Consumos (filas hijas)") List<CardMatrixConsumo> consumos,
        @Schema(description = "Subtotales por mes alineados a las columnas") List<BigDecimal> subtotalsByMonth
) {}
