package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Valuación (corte de precio) de un FCI Cuotapartes")
public record InvestmentValuationResponse(

    @Schema(description = "ID único de la valuación")
    UUID id,

    @Schema(description = "Fecha de la valuación", example = "2026-06-01")
    LocalDate valuationDate,

    @Schema(description = "Precio por cuotaparte en la fecha indicada", example = "1500.0000")
    BigDecimal pricePerUnit,

    @Schema(description = "Fuente de la valuación", example = "MANUAL", allowableValues = {"MANUAL", "ARGENTINADATOS", "DOCTA_CAPITAL"})
    String source,

    @Schema(description = "Fecha de creación del registro")
    OffsetDateTime createdAt
) {}
