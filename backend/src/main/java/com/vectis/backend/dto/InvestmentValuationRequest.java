package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para registrar o actualizar una valuación (corte de precio) de un FCI Cuotapartes")
public record InvestmentValuationRequest(

    @Schema(description = "Fecha de la valuación", example = "2026-06-01")
    @NotNull(message = "La fecha de valuación es obligatoria")
    LocalDate valuationDate,

    @Schema(description = "Precio por cuotaparte en la fecha indicada", example = "1500.0000")
    @NotNull(message = "El precio por cuotaparte es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a cero")
    BigDecimal pricePerUnit
) {}
