package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Resultado de confirmar el cobro de un pago del calendario")
public record ConfirmPaymentResponse(

    @Schema(description = "El pago ya actualizado a estado COBRADO")
    InvestmentPaymentResponse payment,

    @Schema(description = "Cantidad de transacciones generadas (0, 1 o 2)", example = "2")
    int transactionsCreated,

    @Schema(description = "Indica si, tras confirmar este pago, el activo pasó automáticamente a COBRADA "
            + "(pago final: residual 0 y sin más pagos PENDIENTE)", example = "false")
    boolean assetCollected
) {}
