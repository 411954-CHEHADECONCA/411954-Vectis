package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Solicitud de transferencia entre cuentas propias del usuario")
public record TransferRequest(

        @Schema(description = "ID de la cuenta origen", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull UUID sourceAccountId,

        @Schema(description = "ID de la cuenta destino", example = "7ab12c34-1234-5678-abcd-ef0123456789")
        @NotNull UUID destAccountId,

        @Schema(description = "Monto a transferir en la moneda de la cuenta origen", example = "50000.00")
        @NotNull @Positive BigDecimal sourceAmount,

        @Schema(description = "Monto acreditado en la moneda de la cuenta destino (null si misma moneda)", example = "52.50")
        @Nullable BigDecimal destAmount,

        @Schema(description = "Fecha de la transferencia", example = "2026-06-21")
        @NotNull LocalDate transactionDate,

        @Schema(description = "Descripción opcional de la transferencia", example = "Recarga Mercado Pago")
        @Nullable String description
) {}
