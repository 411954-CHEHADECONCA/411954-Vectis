package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Estado de sincronización de las APIs de mercado")
public record MarketApiStatusDto(

    @Schema(description = "Total de snapshots FCI en caché", example = "74")
    long fciSnapshotsTotal,

    @Schema(description = "Fecha del último snapshot FCI sincronizado", example = "2026-06-25")
    LocalDate fciLastSync,

    @Schema(description = "Indica si PPI (Portfolio Personal Inversiones) está configurado con credenciales", example = "true")
    boolean ppiConfigured,

    @Schema(description = "Fecha de la valuación PPI (LETRA/BONO/ON) más reciente registrada, o null si nunca se sincronizó",
            example = "2026-07-07", nullable = true)
    LocalDate ppiLastSync,

    @Schema(description = "Fecha de la cotización MEP más reciente registrada, o null si nunca se sincronizó",
            example = "2026-07-07", nullable = true)
    LocalDate mepLastUpdate,

    @Schema(description = "Fecha de la cotización oficial más reciente registrada, o null si nunca se sincronizó",
            example = "2026-07-07", nullable = true)
    LocalDate oficialLastUpdate
) {}
