package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para editar un pago del calendario (solo PENDIENTE) — todos los campos son "
        + "opcionales, sólo se actualiza lo que venga presente")
public record InvestmentPaymentUpdateRequest(

    @Schema(description = "Nueva fecha de corte (opcional)", example = "2026-07-09", nullable = true)
    LocalDate cuttingDate,

    @Schema(description = "Monto absoluto de renta a cobrar (opcional)", example = "2700.0000", nullable = true)
    @DecimalMin(value = "0", message = "El monto de renta no puede ser negativo")
    BigDecimal rentAmount,

    @Schema(description = "Monto absoluto de amortización a cobrar (opcional)", example = "80000.0000", nullable = true)
    @DecimalMin(value = "0", message = "El monto de amortización no puede ser negativo")
    BigDecimal amortizationAmount
) {}
