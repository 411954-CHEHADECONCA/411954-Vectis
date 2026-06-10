package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Cuenta líquida del usuario")
public record AccountResponse(

    @Schema(description = "ID único de la cuenta", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Nombre de la cuenta", example = "Cuenta Galicia")
    String name,

    @Schema(description = "Tipo de cuenta", example = "Banco")
    String kind,

    @Schema(description = "Detalle adicional", example = "Caja de Ahorro $")
    String detail,

    @Schema(description = "Moneda", example = "ARS")
    String ccy,

    @Schema(description = "Saldo actual", example = "150000.0000")
    BigDecimal balance,

    @Schema(description = "Indica si la cuenta genera intereses", example = "true")
    boolean remunerada,

    @Schema(description = "TNA estimada (null si no es remunerada)", example = "81.0000")
    BigDecimal tna,

    @Schema(description = "Fecha de creación")
    OffsetDateTime createdAt,

    @Schema(description = "Fecha de última actualización")
    OffsetDateTime updatedAt
) {}
