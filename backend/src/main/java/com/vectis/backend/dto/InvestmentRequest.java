package com.vectis.backend.dto;

import com.vectis.backend.domain.entity.InvestmentAssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Datos para crear o editar un activo de inversión")
public record InvestmentRequest(

    @Schema(description = "Nombre del activo", example = "LECAP S31G5")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    String name,

    @Schema(description = "Tipo de activo", example = "LETRA",
            implementation = InvestmentAssetType.class)
    @NotNull(message = "El tipo es obligatorio")
    InvestmentAssetType type,

    @Schema(description = "Moneda del activo", example = "ARS", allowableValues = {"ARS", "USD"})
    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(ARS|USD)$", message = "La moneda debe ser ARS o USD")
    String currency,

    @Schema(description = "Capital invertido (0 para FCI — calculado desde movimientos)", example = "1000000.00")
    @NotNull(message = "El capital es obligatorio")
    @DecimalMin(value = "0", message = "El capital no puede ser negativo")
    BigDecimal principal,

    @Schema(description = "Fecha de compra", example = "2026-01-15")
    @NotNull(message = "La fecha de compra es obligatoria")
    LocalDate purchaseDate,

    @Schema(description = "Fecha de vencimiento (null si no aplica)", example = "2026-08-31")
    LocalDate maturityDate,

    @Schema(description = "TNA en porcentaje (obligatorio para Plazo Fijo y Cuenta Remunerada)", example = "65.00")
    @DecimalMin(value = "0", message = "La TNA no puede ser negativa")
    BigDecimal tna,

    @Schema(description = "ID de la cuenta asociada (opcional)")
    UUID accountId,

    @Schema(description = "Activar seguimiento automático de precios de mercado")
    boolean autoTrack,

    @Schema(description = "Identificador externo del instrumento (nombre exacto del fondo FCI o ticker)", example = "Cocos Capital - Clase A")
    @Size(max = 255)
    String externalId
) {

    @AssertTrue(message = "La fecha de vencimiento debe ser posterior a la fecha de compra")
    public boolean isMaturityDateValid() {
        if (maturityDate == null || purchaseDate == null) {
            return true;
        }
        return maturityDate.isAfter(purchaseDate);
    }

    @AssertTrue(message = "El capital debe ser mayor a cero para este tipo de activo")
    public boolean isPrincipalValid() {
        if (type == null || principal == null) return true;
        if (type == InvestmentAssetType.FCI || type == InvestmentAssetType.FCI_CUOTAPARTES
                || type == InvestmentAssetType.LETRA || type == InvestmentAssetType.BONO
                || type == InvestmentAssetType.ON) {
            return principal.compareTo(BigDecimal.ZERO) >= 0;
        }
        return principal.compareTo(new BigDecimal("0.01")) >= 0;
    }

    @AssertTrue(message = "La TNA es obligatoria para Plazo Fijo y Cuenta Remunerada")
    public boolean isTnaRequiredForType() {
        if (type == null) return true;
        if (type == InvestmentAssetType.PLAZO_FIJO || type == InvestmentAssetType.FCI) {
            return tna != null;
        }
        return true;
    }

    @AssertTrue(message = "El identificador externo es obligatorio cuando se activa el seguimiento automático")
    public boolean isExternalIdRequiredForAutoTrack() {
        if (autoTrack) return externalId != null && !externalId.isBlank();
        return true;
    }
}
