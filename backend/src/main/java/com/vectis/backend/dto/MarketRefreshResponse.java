package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resumen del refresh on-demand de datos de mercado, una entrada por fuente "
        + "(mep, oficial, fci, ppi)")
public record MarketRefreshResponse(

    @ArraySchema(schema = @Schema(implementation = MarketSourceRefreshResult.class))
    List<MarketSourceRefreshResult> sources
) {}
