package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Plan de cuotas activo (consumo financiado)")
public record InstallmentPlanResponse(
        @Schema(description = "ID del grupo de cuotas") UUID groupId,
        @Schema(description = "Descripción del consumo", example = "Notebook Lenovo") String description,
        @Schema(description = "Nombre de la tarjeta", example = "Galicia ····4821") String cardName,
        @Schema(description = "Ícono de la categoría") String categoryIcon,
        @Schema(description = "Color de la categoría") String categoryColor,
        @Schema(description = "Cuota actual (próxima pendiente)", example = "3") int currentNumber,
        @Schema(description = "Total de cuotas", example = "6") int totalInstallments,
        @Schema(description = "Monto de la cuota mensual", example = "102050.0000") BigDecimal monthlyAmount,
        @Schema(description = "Moneda", example = "ARS") String ccy
) {}
