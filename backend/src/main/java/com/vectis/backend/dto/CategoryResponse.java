package com.vectis.backend.dto;

import com.vectis.backend.domain.entity.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Categoría de movimiento")
public record CategoryResponse(

    @Schema(description = "ID único de la categoría", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Nombre de la categoría", example = "Alimentos")
    String name,

    @Schema(description = "Nombre del ícono Lucide", example = "utensils")
    String icon,

    @Schema(description = "Color hexadecimal", example = "#10B981")
    String color,

    @Schema(description = "Tipo de movimiento", example = "EXPENSE")
    CategoryType type,

    @Schema(description = "Indica si es una categoría predefinida del sistema", example = "true")
    boolean isDefault,

    @Schema(description = "Monto estimado mensual para el mes en curso", example = "50000.00", nullable = true)
    BigDecimal estimatedAmount
) {}
