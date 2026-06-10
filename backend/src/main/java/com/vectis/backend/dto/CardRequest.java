package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Datos para crear o editar una tarjeta de crédito")
public record CardRequest(

    @Schema(description = "Nombre del banco emisor", example = "Galicia")
    @NotBlank(message = "El banco es obligatorio")
    @Size(max = 100, message = "El banco no puede superar los 100 caracteres")
    String bank,

    @Schema(description = "Red de la tarjeta", example = "Visa",
            allowableValues = {"Visa", "Mastercard", "Amex"})
    @NotBlank(message = "La red es obligatoria")
    @Pattern(regexp = "^(Visa|Mastercard|Amex)$",
             message = "La red debe ser Visa, Mastercard o Amex")
    String network,

    @Schema(description = "Últimos 4 dígitos", example = "1234")
    @NotBlank(message = "Los últimos 4 dígitos son obligatorios")
    @Pattern(regexp = "^\\d{4}$", message = "Deben ser exactamente 4 dígitos")
    String last4,

    @Schema(description = "Moneda", example = "ARS", allowableValues = {"ARS", "USD"})
    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(ARS|USD)$", message = "La moneda debe ser ARS o USD")
    String ccy,

    @Schema(description = "Límite de crédito", example = "500000.00")
    @NotNull(message = "El límite de crédito es obligatorio")
    @DecimalMin(value = "0", message = "El límite no puede ser negativo")
    BigDecimal creditLimit,

    @Schema(description = "Día de cierre del resumen (1–28)", example = "15")
    @NotNull(message = "El día de cierre es obligatorio")
    @Min(value = 1, message = "El día de cierre debe ser al menos 1")
    @Max(value = 31, message = "El día de cierre no puede superar 31")
    Integer closingDay,

    @Schema(description = "Día de vencimiento del resumen (1–28)", example = "5")
    @NotNull(message = "El día de vencimiento es obligatorio")
    @Min(value = 1, message = "El día de vencimiento debe ser al menos 1")
    @Max(value = 31, message = "El día de vencimiento no puede superar 31")
    Integer dueDay,

    @Schema(description = "Color de acento para el gradiente de la tarjeta", example = "#52eacd")
    @NotBlank(message = "El color de acento es obligatorio")
    @Size(max = 20)
    String accent
) {}
