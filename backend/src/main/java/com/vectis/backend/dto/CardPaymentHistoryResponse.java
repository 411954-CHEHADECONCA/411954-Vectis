package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Historial de un pago de liquidación de tarjeta")
public record CardPaymentHistoryResponse(
        @Schema(description = "ID de la transacción de pago") UUID paymentTransactionId,
        @Schema(description = "ID de la tarjeta pagada") UUID cardId,
        @Schema(description = "Nombre de la tarjeta", example = "Santander ····1234") String cardName,
        @Schema(description = "Año del período pagado", example = "2026") int periodYear,
        @Schema(description = "Mes del período pagado (1–12)", example = "6") int periodMonth,
        @Schema(description = "Fecha en que se registró el pago") LocalDate paidDate,
        @Schema(description = "Monto total abonado") BigDecimal paidAmount,
        @Schema(description = "Moneda del pago", example = "ARS") String ccy,
        @Schema(description = "Cuotas incluidas en el pago") List<CardPaymentCuotaItem> cuotas,
        @Schema(description = "Cargos adicionales registrados con el pago") List<CardPaymentExtraItem> extraCharges
) {}
