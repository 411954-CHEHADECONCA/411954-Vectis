package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Cuota de tarjeta incluida en un pago de liquidación")
public record CardPaymentCuotaItem(
        @Schema(description = "Descripción del consumo", example = "Netflix") String description,
        @Schema(description = "Etiqueta de cuota (k/N), null si es pago único", example = "2/12") String installmentLabel,
        @Schema(description = "Monto de la cuota") BigDecimal amount,
        @Schema(description = "Fecha de vencimiento de la cuota") LocalDate dueDate,
        @Schema(description = "Ícono de la categoría") String categoryIcon,
        @Schema(description = "Color de la categoría") String categoryColor
) {}
