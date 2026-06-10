package com.vectis.backend.dto;

import com.vectis.backend.domain.entity.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Datos para crear o editar una categoría personalizada")
public record CategoryRequest(

    @Schema(description = "Nombre de la categoría", example = "Gym")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String name,

    @Schema(description = "Nombre del ícono Lucide", example = "dumbbell")
    @Size(max = 50, message = "El ícono no puede superar los 50 caracteres")
    String icon,

    @Schema(description = "Color hexadecimal", example = "#10B981")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un valor hexadecimal válido (ej: #10B981)")
    String color,

    @Schema(description = "Tipo de movimiento al que aplica", example = "EXPENSE")
    @NotNull(message = "El tipo es obligatorio")
    CategoryType type,

    @Schema(description = "Monto estimado mensual para el mes en curso (opcional)", example = "50000.00", nullable = true)
    @DecimalMin(value = "0", message = "El monto estimado no puede ser negativo")
    BigDecimal estimatedAmount
) {}
