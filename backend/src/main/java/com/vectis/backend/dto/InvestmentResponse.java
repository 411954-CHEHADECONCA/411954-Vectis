package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Builder
@Schema(description = "Activo de inversión del usuario")
public record InvestmentResponse(

    @Schema(description = "ID único del activo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Nombre del activo", example = "LECAP S31G5")
    String name,

    @Schema(description = "Tipo de activo", example = "LETRA")
    String type,

    @Schema(description = "Moneda", example = "ARS")
    String currency,

    @Schema(description = "Capital invertido (costo de adquisición para la familia cuotapartes; ya incluye intereses capitalizados para FCI)", example = "1000000.0000")
    BigDecimal principal,

    @Schema(description = "Valor de mercado actual del activo. Igual a `principal` para FCI y PLAZO_FIJO; "
            + "para FCI_CUOTAPARTES/LETRA/BONO/ON es la tenencia valuada al último precio registrado "
            + "(o el propio `principal` si todavía no hay valuación cargada)", example = "1050000.0000")
    BigDecimal currentValue,

    @Schema(description = "Fecha de compra", example = "2026-01-15")
    LocalDate purchaseDate,

    @Schema(description = "Fecha de vencimiento (null si no aplica)", example = "2026-08-31")
    LocalDate maturityDate,

    @Schema(description = "TNA en porcentaje", example = "65.0000")
    BigDecimal tna,

    @Schema(description = "ID de la cuenta asociada (null si no hay cuenta vinculada)")
    UUID accountId,

    @Schema(description = "Nombre de la cuenta asociada (null si no hay cuenta vinculada)", example = "Cuenta Galicia")
    String accountName,

    @Schema(description = "Fecha de creación")
    OffsetDateTime createdAt,

    @Schema(description = "Fecha de última actualización")
    OffsetDateTime updatedAt,

    @Schema(description = "Seguimiento automático de precios activado")
    boolean autoTrack,

    @Schema(description = "Identificador externo del instrumento vinculado")
    String externalId,

    @Schema(description = "Movimientos del activo (suscripciones y rescates). Poblado sólo para FCI y FCI_CUOTAPARTES.")
    List<InvestmentMovementResponse> movements,

    @Schema(description = "Valuaciones del activo (cortes de precio). Poblado sólo para FCI_CUOTAPARTES.")
    List<InvestmentValuationResponse> valuations,

    @Schema(description = "Indica si esta inversión participa del flujo de caja", example = "true")
    boolean includeInCashflow,

    @Schema(description = "Estado del activo: ACTIVA o COBRADA (liquidada)", example = "ACTIVA")
    String status,

    @Schema(description = "Fecha de cobro (null si el activo sigue ACTIVA)", example = "2026-07-05")
    LocalDate collectDate
) {}
