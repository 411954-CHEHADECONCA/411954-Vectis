package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Resultado del pago de una liquidación de tarjeta de crédito")
public record CardPaymentResponse(

        @Schema(description = "Cantidad de cuotas marcadas como pagadas en el período", example = "3")
        int cuotasMarcadas,

        @Schema(description = "ID de la transacción de débito creada en la cuenta", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID paymentTransactionId,

        @Schema(description = "Cantidad de cargos extra creados como transacciones adicionales", example = "2")
        int extraChargesCreated
) {}
