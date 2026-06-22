package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resultado de una transferencia entre cuentas")
public record TransferResponse(

        @Schema(description = "UUID compartido por las dos legs de esta transferencia")
        UUID transferGroupId,

        @Schema(description = "ID de la transacción de débito (cuenta origen)")
        UUID sourceTransactionId,

        @Schema(description = "ID de la transacción de crédito (cuenta destino)")
        UUID destTransactionId,

        @Schema(description = "ID de la cuenta origen")
        UUID sourceAccountId,

        @Schema(description = "Nombre de la cuenta origen", example = "Galicia ARS")
        String sourceAccountName,

        @Schema(description = "ID de la cuenta destino")
        UUID destAccountId,

        @Schema(description = "Nombre de la cuenta destino", example = "Mercado Pago")
        String destAccountName,

        @Schema(description = "Monto debitado en la cuenta origen", example = "50000.0000")
        BigDecimal sourceAmount,

        @Schema(description = "Moneda de la cuenta origen", example = "ARS")
        String sourceCcy,

        @Schema(description = "Monto acreditado en la cuenta destino", example = "52.5000")
        BigDecimal destAmount,

        @Schema(description = "Moneda de la cuenta destino", example = "USD")
        String destCcy,

        @Schema(description = "Fecha de la transferencia", example = "2026-06-21")
        LocalDate transactionDate,

        @Schema(description = "Timestamp de creación")
        OffsetDateTime createdAt
) {}
