package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Tarjeta de crédito del usuario")
public record CardResponse(
    UUID id,
    String bank,
    String network,
    String last4,
    String ccy,
    BigDecimal creditLimit,
    int closingDay,
    int dueDay,
    String accent,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
