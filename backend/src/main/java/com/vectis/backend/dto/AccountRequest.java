package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Datos para crear o editar una cuenta líquida")
public record AccountRequest(

    @Schema(description = "Nombre de la cuenta", example = "Cuenta Galicia")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String name,

    @Schema(description = "Tipo de cuenta", example = "Banco", allowableValues = {"Banco", "Billetera", "Efectivo"})
    @NotBlank(message = "El tipo de cuenta es obligatorio")
    @Pattern(regexp = "^(Banco|Billetera|Efectivo)$", message = "El tipo debe ser Banco, Billetera o Efectivo")
    String kind,

    @Schema(description = "Detalle adicional (banco, alias, etc.)", example = "Caja de Ahorro $")
    @Size(max = 100, message = "El detalle no puede superar los 100 caracteres")
    String detail,

    @Schema(description = "Moneda de la cuenta", example = "ARS", allowableValues = {"ARS", "USD"})
    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(ARS|USD)$", message = "La moneda debe ser ARS o USD")
    String ccy,

    @Schema(description = "Saldo inicial de la cuenta", example = "150000.00")
    @NotNull(message = "El saldo es obligatorio")
    @DecimalMin(value = "0", message = "El saldo no puede ser negativo")
    BigDecimal balance,

    @Schema(description = "Si la cuenta se incluye en el cálculo de cashflow mensual", example = "true")
    boolean includeInCashflow
) {
}
