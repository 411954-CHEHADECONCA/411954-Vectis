package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para confirmar el cobro de un pago del calendario")
public record ConfirmPaymentRequest(

    @Schema(description = "Fecha en la que se confirma el cobro. Debe pertenecer a un mes de cashflow abierto",
            example = "2026-07-09")
    @NotNull(message = "La fecha de cobro es obligatoria")
    LocalDate collectedDate,

    @Schema(description = "Monto de renta a acreditar (por defecto el estimado del calendario)", example = "2700.0000")
    @NotNull(message = "El monto de renta es obligatorio (usar 0 si no aplica)")
    @DecimalMin(value = "0", message = "El monto de renta no puede ser negativo")
    BigDecimal rentAmount,

    @Schema(description = "Monto de amortización a acreditar (por defecto el estimado del calendario)", example = "80000.0000")
    @NotNull(message = "El monto de amortización es obligatorio (usar 0 si no aplica)")
    @DecimalMin(value = "0", message = "El monto de amortización no puede ser negativo")
    BigDecimal amortizationAmount
) {}
