package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ultimo registro de inflacion mensual (IPC)")
public record InflationResponse(
        @Schema(description = "Tasa mensual de inflacion (porcentual, NUMERIC exacto)", example = "2.4000") String monthlyRate,
        @Schema(description = "Fecha de cierre del periodo (ISO-8601)", example = "2026-05-31") String periodDate,
        @Schema(description = "Fuente de datos", example = "argentinadatos.com") String source
) {}
