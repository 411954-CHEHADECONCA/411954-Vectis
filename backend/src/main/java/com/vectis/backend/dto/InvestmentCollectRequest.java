package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para confirmar el cobro (liquidación) de un activo de inversión")
public record InvestmentCollectRequest(

    @NotNull(message = "La fecha de cobro es obligatoria")
    @Schema(description = "Fecha de cobro elegida por el usuario. Debe pertenecer a un mes de cashflow abierto",
            example = "2026-07-05")
    LocalDate collectDate,

    @Schema(description = "Rendimiento editado manualmente por el usuario (opcional). Sólo se tiene en cuenta "
            + "para PLAZO_FIJO y FCI — se ignora para la familia cuotapartes, donde el rendimiento sale del "
            + "valor de mercado. Si viene null, se usa el precálculo tal cual. Debe ser no-negativo.",
            example = "52000.0000")
    BigDecimal rendimiento
) {}
