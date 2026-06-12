package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Datos para registrar o editar un movimiento")
public record MovementRequest(

        @Schema(description = "Descripción del movimiento", example = "Coto — compra semanal")
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 200, message = "La descripción no puede superar los 200 caracteres")
        String description,

        @Schema(description = "Monto total del movimiento (en cuotas, el total a financiar)", example = "612300.00")
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount,

        @Schema(description = "Moneda del movimiento", example = "ARS", allowableValues = {"ARS", "USD"})
        @NotBlank(message = "La moneda es obligatoria")
        @Pattern(regexp = "^(ARS|USD)$", message = "La moneda debe ser ARS o USD")
        String ccy,

        @Schema(description = "Tipo de movimiento", example = "EXPENSE", allowableValues = {"INCOME", "EXPENSE"})
        @NotBlank(message = "El tipo es obligatorio")
        @Pattern(regexp = "^(INCOME|EXPENSE)$", message = "El tipo debe ser INCOME o EXPENSE")
        String type,

        @Schema(description = "ID de la categoría asociada (opcional)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID categoryId,

        @Schema(description = "ID de la cuenta asociada (opcional, excluyente con cardId)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID accountId,

        @Schema(description = "ID de la tarjeta asociada (opcional, excluyente con accountId)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID cardId,

        @Schema(description = "Fecha de la compra o ingreso", example = "2026-06-11")
        @NotNull(message = "La fecha es obligatoria")
        LocalDate transactionDate,

        @Schema(description = "Cantidad de cuotas (1 = pago único). Mayor a 1 requiere tarjeta y tipo EXPENSE", example = "6")
        @NotNull(message = "La cantidad de cuotas es obligatoria")
        @Min(value = 1, message = "Las cuotas deben ser al menos 1")
        @Max(value = 360, message = "Las cuotas no pueden superar 360")
        Integer installments
) {}
