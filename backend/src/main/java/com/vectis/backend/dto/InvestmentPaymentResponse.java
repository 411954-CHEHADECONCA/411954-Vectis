package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Schema(description = "Fila del calendario de pagos (renta/amortización) de un BONO/ON")
public record InvestmentPaymentResponse(

    @Schema(description = "ID único del pago", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Fecha de corte del pago", example = "2026-07-09")
    LocalDate cuttingDate,

    @Schema(description = "Factor de renta por 100 nominales", example = "0.270000")
    BigDecimal rentPer100,

    @Schema(description = "Factor de amortización por 100 nominales", example = "8.000000")
    BigDecimal amortizationPer100,

    @Schema(description = "Monto de renta estimado (override si existe, si no per100 × nominales tenidos a la fecha / 100)",
            example = "2700.0000")
    BigDecimal estimatedRentAmount,

    @Schema(description = "Monto de amortización estimado (override si existe, si no per100 × nominales tenidos a la fecha / 100)",
            example = "80000.0000")
    BigDecimal estimatedAmortizationAmount,

    @Schema(description = "Moneda de pago", example = "USD")
    String currency,

    @Schema(description = "Estado del pago", example = "PENDIENTE", allowableValues = {"PENDIENTE", "COBRADO", "OMITIDO"})
    String status,

    @Schema(description = "Origen del pago", example = "PPI", allowableValues = {"PPI", "MANUAL"})
    String source,

    @Schema(description = "Indica si el usuario editó manualmente este pago (bloquea el pisado por sync)", example = "false")
    boolean userEdited,

    @Schema(description = "Fecha en la que se confirmó el cobro (null si sigue PENDIENTE/OMITIDO)", example = "2026-07-09", nullable = true)
    LocalDate collectedDate,

    @Schema(description = "ID de la transacción de renta generada al confirmar (null si no aplica)", nullable = true)
    UUID rentTransactionId,

    @Schema(description = "ID de la transacción de amortización generada al confirmar (null si no aplica)", nullable = true)
    UUID amortizationTransactionId,

    @Schema(description = "Residual por 100 nominales luego de este pago (0 indica el pago final)",
            example = "64.000000", nullable = true)
    BigDecimal residualAfterPer100
) {}
