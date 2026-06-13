package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Resumen de tarjetas: caras, total a pagar, próximo vencimiento, cuotas activas y vencimientos")
public record CardOverviewResponse(
        @Schema(description = "Caras de tarjeta") List<CardFaceResponse> cards,
        @Schema(description = "Total a pagar (suma de deudas pendientes)", example = "1032300.0000") BigDecimal totalDue,
        @Schema(description = "Tarjeta del próximo vencimiento", example = "Brubank ····0937") String nextDueCardName,
        @Schema(description = "Fecha del próximo vencimiento", example = "2026-06-28") LocalDate nextDueDate,
        @Schema(description = "Monto del próximo vencimiento", example = "420000.0000") BigDecimal nextDueAmount,
        @Schema(description = "Planes de cuotas activos") List<InstallmentPlanResponse> cuotasActivas,
        @Schema(description = "Próximos vencimientos por tarjeta") List<CardDueResponse> vencimientos
) {}
