package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Movimiento del libro de transacciones")
public record MovementResponse(

        @Schema(description = "ID único del movimiento")
        UUID id,

        @Schema(description = "Tipo de movimiento", example = "EXPENSE")
        String type,

        @Schema(description = "Descripción", example = "Visa — cuota 3/6 notebook")
        String description,

        @Schema(description = "Monto de esta fila/cuota en moneda original", example = "612300.0000")
        BigDecimal amount,

        @Schema(description = "Moneda", example = "ARS")
        String ccy,

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

        @Schema(description = "ID de la tarjeta asociada")
        UUID cardId,

        @Schema(description = "Nombre de la tarjeta asociada", example = "Galicia ····4821")
        String cardName,

        @Schema(description = "Fecha de la compra/ingreso", example = "2026-06-11")
        LocalDate transactionDate,

        @Schema(description = "Fecha en que impacta el movimiento (ancla del período)", example = "2026-07-10")
        LocalDate dueDate,

        @Schema(description = "Indica si el movimiento es una cuota de una compra diferida", example = "true")
        boolean installment,

        @Schema(description = "Número de cuota (1..N), null si es pago único", example = "3")
        Integer installmentNumber,

        @Schema(description = "Total de cuotas, null si es pago único", example = "6")
        Integer totalInstallments,

        @Schema(description = "ID que agrupa las cuotas de una misma compra")
        UUID installmentGroupId,

        @Schema(description = "Fecha de creación")
        OffsetDateTime createdAt
) {}
