package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Movimiento recurrente del usuario")
public record RecurringMovementResponse(

    @Schema(description = "ID único del movimiento recurrente")
    UUID id,

    @Schema(description = "Descripción del movimiento", example = "Suscripción Netflix")
    String description,

    @Schema(description = "Monto del movimiento", example = "15000.0000")
    BigDecimal amount,

    @Schema(description = "Moneda", example = "ARS")
    String ccy,

    @Schema(description = "Tipo de movimiento", example = "EXPENSE")
    String type,

    @Schema(description = "ID de la categoría asociada")
    UUID categoryId,

    @Schema(description = "Nombre de la categoría asociada")
    String categoryName,

    @Schema(description = "Ícono de la categoría asociada")
    String categoryIcon,

    @Schema(description = "Color de la categoría asociada")
    String categoryColor,

    @Schema(description = "ID de la cuenta asociada")
    UUID accountId,

    @Schema(description = "Nombre de la cuenta asociada")
    String accountName,

    @Schema(description = "ID de la tarjeta de crédito asociada")
    UUID cardId,

    @Schema(description = "Nombre de la tarjeta asociada", example = "Galicia ····4821")
    String cardName,

    @Schema(description = "Día del mes en que se aplica el movimiento", example = "10")
    int dayOfMonth,

    @Schema(description = "Indica si el movimiento está activo", example = "true")
    boolean active,

    @Schema(description = "Fecha de creación")
    OffsetDateTime createdAt
) {}
