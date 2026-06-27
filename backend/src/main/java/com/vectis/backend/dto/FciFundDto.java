package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Fondo de inversión FCI disponible en el mercado")
public record FciFundDto(
        @Schema(example = "Cocos Capital - Clase A") String fondo,
        @Schema(example = "mercadoDinero")            String categoria,
        @Schema(example = "1099.3320")                BigDecimal vcp,
        @Schema(example = "2026-06-24")               LocalDate fecha
) {}
