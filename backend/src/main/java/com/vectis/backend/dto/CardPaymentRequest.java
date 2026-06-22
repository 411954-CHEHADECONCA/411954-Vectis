package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Datos para registrar el pago de una liquidación de tarjeta de crédito")
public record CardPaymentRequest(

        @Schema(description = "ID de la cuenta bancaria desde la que se debita el pago",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "La cuenta de débito es obligatoria")
        UUID accountId,

        @Schema(description = "Fecha en que se realizó el pago", example = "2026-07-25")
        @NotNull(message = "La fecha de pago es obligatoria")
        LocalDate paidDate,

        @Schema(description = "Monto total transferido desde la cuenta para pagar la tarjeta", example = "82700.00")
        @NotNull(message = "El monto pagado es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto pagado debe ser mayor a cero")
        BigDecimal paidAmount,

        @Schema(description = "Año del período de la liquidación", example = "2026")
        @NotNull
        @Min(2020) @Max(2099)
        Integer periodYear,

        @Schema(description = "Mes del período de la liquidación (1–12)", example = "7")
        @NotNull
        @Min(1) @Max(12)
        Integer periodMonth,

        @Schema(description = "ID de la categoría para la transacción de pago (opcional)",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID paymentCategoryId,

        @Schema(description = "Cargos extra de la liquidación (intereses, gastos administrativos, etc.)")
        @Valid
        List<@Valid ExtraChargeRequest> extraCharges
) {}
