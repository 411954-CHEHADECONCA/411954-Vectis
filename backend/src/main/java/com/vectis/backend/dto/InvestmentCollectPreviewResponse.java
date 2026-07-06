package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Precálculo del cobro de un activo de inversión a una fecha dada, sin efectos "
        + "(no persiste ni genera transacciones)")
public record InvestmentCollectPreviewResponse(

    @Schema(description = "Capital a acreditar (costo de adquisición o saldo de capital, según el tipo de activo)",
            example = "1000000.0000")
    BigDecimal capital,

    @Schema(description = "Rendimiento calculado a la fecha elegida", example = "50000.0000")
    BigDecimal rendimiento,

    @Schema(description = "Total a acreditar (capital + rendimiento)", example = "1050000.0000")
    BigDecimal total,

    @Schema(description = "Moneda del monto", example = "ARS")
    String currency,

    @Schema(description = "Indica si el rendimiento es editable por el usuario antes de confirmar el cobro "
            + "(true sólo para PLAZO_FIJO y FCI; para la familia cuotapartes el valor de mercado no es editable)",
            example = "true")
    boolean editableRendimiento
) {}
