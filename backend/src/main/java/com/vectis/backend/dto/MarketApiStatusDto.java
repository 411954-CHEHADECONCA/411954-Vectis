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
    boolean ppiConfigured
) {}
