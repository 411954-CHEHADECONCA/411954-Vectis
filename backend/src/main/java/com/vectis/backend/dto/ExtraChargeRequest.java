package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Cargo extra de una liquidación de tarjeta (intereses, gastos administrativos, etc.)")
public record ExtraChargeRequest(

        @Schema(description = "Descripción del cargo", example = "Intereses por pago mínimo")
        @NotBlank(message = "La descripción del cargo es obligatoria")
        @Size(max = 200)
        String description,

        @Schema(description = "Monto del cargo", example = "12500.00")
        @NotNull(message = "El monto del cargo es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto del cargo debe ser mayor a cero")
        BigDecimal amount,

        @Schema(description = "ID de la categoría del cargo (obligatorio)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "La categoría del cargo es obligatoria")
        UUID categoryId
) {}
