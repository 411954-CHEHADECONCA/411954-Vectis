package com.vectis.backend.dto;

import com.vectis.backend.domain.entity.InvestmentMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para registrar un movimiento de inversión (suscripción, rescate o revalúo) "
        + "para cualquier tipo: FCI, FCI_CUOTAPARTES, LETRA, BONO u ON")
public record InvestmentMovementRequest(

    @Schema(description = "Fecha del movimiento", example = "2026-03-15")
    @NotNull(message = "La fecha del movimiento es obligatoria")
    LocalDate movementDate,

    @Schema(description = "Tipo de movimiento", example = "SUSCRIPCION")
    @NotNull(message = "El tipo de movimiento es obligatorio")
    InvestmentMovementType type,

    @Schema(description = "Monto del movimiento", example = "500000.00")
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    BigDecimal amount,

    @Schema(description = "Cuotapartes involucradas (obligatorio para FCI_CUOTAPARTES, null para los demás tipos)",
            example = "400.000000")
    @DecimalMin(value = "0.000001", message = "Las cuotapartes deben ser mayores a cero")
    BigDecimal units,

    @Schema(description = "Precio por cuotaparte/nominal al momento del movimiento. Para activos de la familia "
            + "cuotapartes (FCI_CUOTAPARTES/LETRA/BONO/ON) se persiste como valuación histórica a la fecha del "
            + "movimiento. Opcional: si no viene, se deriva de monto/cuotapartes.",
            example = "1250.5000")
    @DecimalMin(value = "0.000001", message = "El precio debe ser mayor a cero")
    BigDecimal pricePerUnit
) {}
