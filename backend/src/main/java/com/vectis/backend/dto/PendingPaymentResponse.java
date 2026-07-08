package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Schema(description = "Pago del calendario PENDIENTE y vencido — alimenta el badge de pagos por cobrar")
public record PendingPaymentResponse(

    @Schema(description = "ID del pago") UUID paymentId,
    @Schema(description = "ID del activo de inversión al que pertenece") UUID assetId,
    @Schema(description = "Nombre del activo de inversión", example = "BONOS ARGENTINA USD 2030 L.A (AL30)") String assetName,
    @Schema(description = "Fecha de corte del pago", example = "2026-07-09") LocalDate cuttingDate,
    @Schema(description = "Moneda de pago", example = "USD") String currency,
    @Schema(description = "Monto de renta estimado", example = "2700.0000") BigDecimal estimatedRent,
    @Schema(description = "Monto de amortización estimado", example = "80000.0000") BigDecimal estimatedAmortization
) {}
