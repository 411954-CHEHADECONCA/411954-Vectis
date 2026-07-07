package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Resultado del refresh on-demand para una única fuente de datos de mercado")
public record MarketSourceRefreshResult(

    @Schema(description = "Fuente de datos de mercado", example = "mep",
            allowableValues = {"mep", "oficial", "fci", "ppi"})
    String source,

    @Schema(description = "Estado resultante del refresh: refreshed (se actualizó), upToDate (ya estaba al día), "
            + "failed (la fuente externa falló), notConfigured (la integración no tiene credenciales/activos) "
            + "o alreadyRunning (ya hay un refresh en curso; no se volvió a consultar la fuente)",
            example = "refreshed",
            allowableValues = {"refreshed", "upToDate", "failed", "notConfigured", "alreadyRunning"})
    String status,

    @Schema(description = "Fecha del dato más reciente disponible para esta fuente luego del refresh, "
            + "o null si nunca se sincronizó (p. ej. notConfigured)", example = "2026-07-07", nullable = true)
    LocalDate lastUpdate
) {}
