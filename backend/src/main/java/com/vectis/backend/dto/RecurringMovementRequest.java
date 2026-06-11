package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Datos para crear o editar un movimiento recurrente")
public record RecurringMovementRequest(

    @Schema(description = "Descripción del movimiento", example = "Suscripción Netflix")
    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 200, message = "La descripción no puede superar los 200 caracteres")
    String description,

    @Schema(description = "Monto del movimiento", example = "15000.00")
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

    @Schema(description = "ID de la tarjeta de crédito asociada (opcional, excluyente con accountId)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID cardId,

    @Schema(description = "Día del mes en que se aplica el movimiento", example = "10")
    @NotNull(message = "El día del mes es obligatorio")
    @Min(value = 1, message = "El día del mes debe ser al menos 1")
    @Max(value = 31, message = "El día del mes no puede superar 31")
    Integer dayOfMonth
) {}
