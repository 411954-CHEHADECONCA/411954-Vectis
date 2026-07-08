package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para dar de alta manualmente un pago del calendario (fuente MANUAL)")
public record InvestmentPaymentRequest(

    @Schema(description = "Fecha de corte del pago", example = "2026-07-09")
    @NotNull(message = "La fecha de corte es obligatoria")
    LocalDate cuttingDate,

    @Schema(description = "Moneda de pago", example = "USD", allowableValues = {"ARS", "USD"})
    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(ARS|USD)$", message = "La moneda debe ser ARS o USD")
    String currency,

    @Schema(description = "Monto absoluto de renta a cobrar (opcional, default 0 si se omite)", example = "2700.0000")
    @DecimalMin(value = "0", message = "El monto de renta no puede ser negativo")
    BigDecimal rentAmount,

    @Schema(description = "Monto absoluto de amortización a cobrar (opcional, default 0 si se omite)", example = "80000.0000")
    @DecimalMin(value = "0", message = "El monto de amortización no puede ser negativo")
    BigDecimal amortizationAmount
) {
    /** Monto de renta normalizado: 0 si se omitió. */
    public BigDecimal rentAmountOrZero() {
        return rentAmount != null ? rentAmount : BigDecimal.ZERO;
    }

    /** Monto de amortización normalizado: 0 si se omitió. */
    public BigDecimal amortizationAmountOrZero() {
        return amortizationAmount != null ? amortizationAmount : BigDecimal.ZERO;
    }
}
