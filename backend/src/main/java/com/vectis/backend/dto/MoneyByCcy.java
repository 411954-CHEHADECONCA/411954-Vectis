package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monto desglosado por moneda. Todo agregado del cashflow es bimonetario (ARS y USD conviven en la
 * misma cuenta de usuario), así que ningún total puede colapsarse a un único {@link BigDecimal} sin
 * asumir implícitamente que todo es ARS — ese fue exactamente el bug de cobros de bonos en USD
 * apareciendo como pesos. Espeja la interfaz {@code PortfolioByCurrency} del frontend
 * (ver {@code PortfolioSummaryService}): este DTO tampoco resuelve la conversión ARS↔USD, eso queda
 * del lado del consumidor (frontend con {@code CurrencyService}, o {@link CashflowService} internamente
 * para los pocos porcentajes que sí necesitan normalizar a un valor único, usando la cotización
 * OFICIAL del período).
 */
@Schema(description = "Monto desglosado por moneda (ARS y USD)")
public record MoneyByCcy(

    @Schema(description = "Monto en pesos argentinos", example = "125000.0000")
    BigDecimal ars,

    @Schema(description = "Monto en dólares estadounidenses", example = "50.0000")
    BigDecimal usd
) {
    private static final MathContext MC = MathContext.DECIMAL128;

    public MoneyByCcy {
        Objects.requireNonNull(ars, "ars");
        Objects.requireNonNull(usd, "usd");
    }

    public static MoneyByCcy zero() {
        return new MoneyByCcy(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public MoneyByCcy add(MoneyByCcy other) {
        return new MoneyByCcy(ars.add(other.ars, MC), usd.add(other.usd, MC));
    }

    public MoneyByCcy subtract(MoneyByCcy other) {
        return new MoneyByCcy(ars.subtract(other.ars, MC), usd.subtract(other.usd, MC));
    }

    /** Suma {@code amount} al bucket correspondiente según {@code ccy} ("ARS" o "USD"). */
    public MoneyByCcy plusIn(String ccy, BigDecimal amount) {
        if (amount == null) return this;
        if ("USD".equals(ccy)) {
            return new MoneyByCcy(ars, usd.add(amount, MC));
        }
        return new MoneyByCcy(ars.add(amount, MC), usd);
    }

    public MoneyByCcy setScale(int scale, RoundingMode rm) {
        return new MoneyByCcy(ars.setScale(scale, rm), usd.setScale(scale, rm));
    }

    /** {@code true} si ambos buckets son cero. */
    public boolean isZero() {
        return ars.signum() == 0 && usd.signum() == 0;
    }
}
