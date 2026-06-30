package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Datos para editar un movimiento de una Cuenta Remunerada (FCI).
 * Ambos campos son opcionales: {@code amount} ajusta el monto del movimiento
 * (tramo REVALUO, capitaliza), y {@code interestOverride} fija el interés del
 * tramo SUSCRIPCION/RESCATE (no capitaliza). Enviar {@code interestOverride}
 * en null restaura el cálculo por TNA.
 */
@Schema(description = "Datos para editar un movimiento de una Cuenta Remunerada (FCI)")
public record InvestmentMovementUpdateRequest(

    @Schema(description = "Nuevo monto del movimiento (para tramo REVALUO; capitaliza al saldo)",
            example = "500000.00")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    BigDecimal amount,

    @Schema(description = "Override del interés del tramo (para tramo SUSCRIPCION/RESCATE; no capitaliza). "
            + "Null restaura el cálculo por TNA.",
            example = "12500.00")
    @DecimalMin(value = "0.00", message = "El interés del tramo no puede ser negativo")
    BigDecimal interestOverride
) {}
