package com.vectis.backend.util;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentValuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;

/**
 * Cálculo del valor de mercado actual de un activo de inversión — única fuente de verdad usada tanto
 * por {@code InvestmentService#collectInvestment} (monto a acreditar al cobrar la inversión) como por
 * {@code InvestmentMapper} (campo {@code currentValue} expuesto en {@code InvestmentResponse}), para
 * que ambos no terminen calculando el mismo número por caminos distintos y divergiendo con el tiempo.
 *
 * <p>Réplica del cálculo ya existente en el frontend ({@code calcValorActualCP} en
 * {@code inversiones.component.ts}).
 *
 * <p>Sin estado y sin dependencias — clase de utilidad con métodos estáticos, no un bean de Spring.
 */
public final class InvestmentValuationCalculator {

    private static final int MONEY_SCALE = 4;
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal HUNDRED      = BigDecimal.valueOf(100);

    private InvestmentValuationCalculator() {
    }

    /** Tipos cuyo rendimiento deriva de valuaciones/precio de mercado (no de TNA). */
    public static boolean isCuotaparteFamily(InvestmentAssetType type) {
        return type == InvestmentAssetType.FCI_CUOTAPARTES
                || type == InvestmentAssetType.LETRA
                || type == InvestmentAssetType.BONO
                || type == InvestmentAssetType.ON;
    }

    /** Nominales/cuotapartes netos en tenencia: suma de unidades SUSCRIPCION menos RESCATE. */
    public static BigDecimal calcHeldUnits(InvestmentAsset asset) {
        return asset.getMovements().stream()
                .filter(m -> m.getUnits() != null)
                .map(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                        ? m.getUnits()
                        : m.getUnits().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Variante de {@link #calcHeldUnits} a una fecha arbitraria: sólo considera los movimientos con
     * {@code movementDate <= asOfDate}. Usada por {@code InvestmentPaymentService} para calcular el
     * monto real de un pago de calendario (renta/amortización), que se estima con los nominales
     * tenidos a la fecha de corte del pago — no con la tenencia actual — así comprar más nominales
     * después de generado el calendario no infla retroactivamente pagos ya pasados.
     */
    public static BigDecimal unitsHeldAsOf(InvestmentAsset asset, LocalDate asOfDate) {
        return asset.getMovements().stream()
                .filter(m -> m.getUnits() != null && !m.getMovementDate().isAfter(asOfDate))
                .map(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                        ? m.getUnits()
                        : m.getUnits().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Valor de mercado actual del activo.
     *
     * <p>FCI (Cuenta Remunerada): {@code principal} ya capitaliza los REVALUO, así que ya es el valor
     * actual. PLAZO_FIJO: {@code principal} es fijo/definido por el usuario, no hay valuación de
     * mercado que aplicar.
     *
     * <p>Familia cuotapartes (FCI_CUOTAPARTES/LETRA/BONO/ON): {@code principal} sólo refleja el costo
     * de adquisición (SUSCRIPCION − RESCATE); el valor real es la tenencia (nominales/cuotapartes) ×
     * el último {@code pricePerUnit} registrado.
     *
     * <p>Si el activo no tiene movimientos registrados (sin unidades para valuar) se usa el principal
     * tal cual, como mejor aproximación disponible. Si hay movimientos pero todavía no hay ninguna
     * valuación cargada: LETRA (cero cupón, VN=1 por nominal) usa la tenencia como valor de rescate;
     * el resto cae también al principal (costo de adquisición) por falta de precio de mercado.
     */
    public static BigDecimal calculateCurrentValue(InvestmentAsset asset) {
        if (!isCuotaparteFamily(asset.getType())) {
            return asset.getPrincipal();
        }
        if (asset.getMovements().isEmpty()) {
            return asset.getPrincipal();
        }

        BigDecimal held = calcHeldUnits(asset);
        Optional<InvestmentValuation> latest = asset.getValuations().stream()
                .max(Comparator.comparing(InvestmentValuation::getValuationDate));

        if (latest.isEmpty()) {
            if (asset.getType() == InvestmentAssetType.LETRA) {
                return held.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
            }
            return asset.getPrincipal();
        }

        return held.multiply(latest.get().getPricePerUnit()).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Valor de mercado de la familia cuotapartes a una fecha arbitraria (no necesariamente hoy): usa
     * la última valuación con {@code valuationDate <= asOfDate} en vez de la última valuación global.
     * Si ninguna valuación califica para esa fecha, aplica el mismo fallback que
     * {@link #calculateCurrentValue}: LETRA usa la tenencia a VN=1; el resto cae al principal.
     *
     * <p>Sólo tiene sentido para la familia cuotapartes — para el resto de los tipos delega en
     * {@link #calculateCurrentValue}, que no depende de la fecha.
     */
    public static BigDecimal calculateValueAsOf(InvestmentAsset asset, LocalDate asOfDate) {
        if (!isCuotaparteFamily(asset.getType())) {
            return calculateCurrentValue(asset);
        }
        if (asset.getMovements().isEmpty()) {
            return asset.getPrincipal();
        }

        BigDecimal held = calcHeldUnits(asset);
        Optional<InvestmentValuation> latestAsOf = asset.getValuations().stream()
                .filter(v -> !v.getValuationDate().isAfter(asOfDate))
                .max(Comparator.comparing(InvestmentValuation::getValuationDate));

        if (latestAsOf.isEmpty()) {
            if (asset.getType() == InvestmentAssetType.LETRA) {
                return held.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
            }
            return asset.getPrincipal();
        }

        return held.multiply(latestAsOf.get().getPricePerUnit()).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    /** Días transcurridos entre dos fechas, nunca negativo (0 si {@code to} no es posterior a {@code from}). */
    private static long daysBetweenFloored(LocalDate from, LocalDate to) {
        long dias = ChronoUnit.DAYS.between(from, to);
        return Math.max(dias, 0L);
    }

    /** Interés simple por TNA: {@code base * (tna/100) * dias/365}, escala 4, HALF_EVEN. */
    private static BigDecimal simpleInterest(BigDecimal base, BigDecimal tna, long dias) {
        if (dias <= 0 || tna == null || tna.signum() == 0 || base.signum() == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RM);
        }
        BigDecimal tasaDiaria = tna.divide(HUNDRED, MC).divide(DAYS_IN_YEAR, MC);
        return base.multiply(tasaDiaria, MC)
                .multiply(BigDecimal.valueOf(dias), MC)
                .setScale(MONEY_SCALE, RM);
    }

    /** Monto estimado de renta/amortización de un pago de calendario (ver {@link #estimatePaymentAmounts}). */
    public record EstimatedPaymentAmounts(BigDecimal rent, BigDecimal amortization) {
    }

    /**
     * Única fuente de verdad para estimar el monto de renta y amortización de un
     * {@code InvestmentPayment} del calendario de pagos de BONO/ON. Usada tanto por
     * {@code InvestmentPaymentService} ({@code toResponse}/{@code toPendingResponse}, el calendario
     * completo del activo) como por {@code InvestmentMapper} ({@code collectedAmortizations} expuesto
     * en {@code InvestmentResponse}), para que ambos caminos no diverjan si la fórmula cambia.
     *
     * <p>Precedencia: si el usuario cargó un override manual ({@code rentAmountOverride}/
     * {@code amortizationAmountOverride}) se usa tal cual; si no, se estima a partir del nominal
     * tenido a la fecha de corte del pago ({@link #unitsHeldAsOf}) y el {@code per100} del calendario.
     */
    public static EstimatedPaymentAmounts estimatePaymentAmounts(InvestmentPayment payment, InvestmentAsset asset) {
        BigDecimal unitsHeld = unitsHeldAsOf(asset, payment.getCuttingDate());

        BigDecimal estimatedRent = payment.getRentAmountOverride() != null
                ? payment.getRentAmountOverride()
                : payment.getRentPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);
        BigDecimal estimatedAmortization = payment.getAmortizationAmountOverride() != null
                ? payment.getAmortizationAmountOverride()
                : payment.getAmortizationPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);

        return new EstimatedPaymentAmounts(estimatedRent, estimatedAmortization);
    }

    /** Split capital/rendimiento resultante de cobrar (liquidar) un activo. */
    public record CollectBreakdown(BigDecimal capital, BigDecimal rendimiento) {
        public BigDecimal total() {
            return capital.add(rendimiento);
        }
    }

    /**
     * Calcula el split capital/rendimiento de un activo a una fecha de cobro elegida. Delega en la
     * sobrecarga de 3 argumentos con {@code residualCapitalFraction = 1} (ninguna porción del
     * capital fue devuelta todavía vía cupones de amortización) — ver esa sobrecarga para el caso
     * BONO/ON con amortizaciones parciales ya cobradas.
     */
    public static CollectBreakdown calculateCollectBreakdown(InvestmentAsset asset, LocalDate asOfDate) {
        return calculateCollectBreakdown(asset, asOfDate, BigDecimal.ONE);
    }

    /**
     * Calcula el split capital/rendimiento de un activo a una fecha de cobro elegida:
     * <ul>
     *   <li>PLAZO_FIJO: capital = principal; rendimiento = interés simple por TNA desde la fecha de
     *       compra hasta {@code asOfDate}.</li>
     *   <li>FCI (Cuenta Remunerada): capital = suma(SUSCRIPCION) − suma(RESCATE) (excluye REVALUO,
     *       que ya está capitalizado en {@code principal}); rendimiento = interés ya capitalizado por
     *       REVALUOs pasados ({@code principal - capital}) + interés adicional devengado por TNA desde
     *       la fecha del último movimiento hasta {@code asOfDate} (réplica de
     *       {@code accruedInterestForDate} en {@code inversiones.component.ts}).</li>
     *   <li>Familia cuotapartes (FCI_CUOTAPARTES/LETRA/BONO/ON): capital = principal ×
     *       {@code residualCapitalFraction} (costo de adquisición neto de las amortizaciones
     *       parciales ya cobradas vía el calendario de pagos de {@code InvestmentPaymentService} —
     *       evita devolver dos veces la misma porción de capital al cobrar el activo entero después
     *       de haber cobrado cupones de amortización); rendimiento =
     *       {@link #calculateValueAsOf(InvestmentAsset, LocalDate)} menos ese capital.</li>
     * </ul>
     *
     * @param residualCapitalFraction fracción del principal aún no devuelta como capital (1 = nada
     *                                amortizado todavía; {@code 1 − Σ amortization_per_100 cobradas / 100}
     *                                para un BONO/ON con cupones de amortización ya confirmados).
     *                                Sólo se aplica en la rama por defecto (familia cuotapartes);
     *                                PLAZO_FIJO y FCI no tienen calendario de amortización.
     */
    public static CollectBreakdown calculateCollectBreakdown(
            InvestmentAsset asset, LocalDate asOfDate, BigDecimal residualCapitalFraction) {
        return switch (asset.getType()) {
            case PLAZO_FIJO -> {
                BigDecimal capital = asset.getPrincipal();
                long dias = daysBetweenFloored(asset.getPurchaseDate(), asOfDate);
                BigDecimal rendimiento = simpleInterest(capital, asset.getTna(), dias);
                yield new CollectBreakdown(capital, rendimiento);
            }
            case FCI -> {
                BigDecimal capital = asset.getMovements().stream()
                        .filter(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                                || m.getType() == InvestmentMovementType.RESCATE)
                        .map(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                                ? m.getAmount()
                                : m.getAmount().negate())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(MONEY_SCALE, RM);

                BigDecimal capitalizado = asset.getPrincipal().subtract(capital, MC).setScale(MONEY_SCALE, RM);

                Optional<LocalDate> lastMovementDate = asset.getMovements().stream()
                        .map(InvestmentMovement::getMovementDate)
                        .max(Comparator.naturalOrder());
                BigDecimal accrued = lastMovementDate
                        .map(last -> simpleInterest(asset.getPrincipal(), asset.getTna(),
                                daysBetweenFloored(last, asOfDate)))
                        .orElse(BigDecimal.ZERO.setScale(MONEY_SCALE, RM));

                BigDecimal rendimiento = capitalizado.add(accrued, MC).setScale(MONEY_SCALE, RM);
                yield new CollectBreakdown(capital, rendimiento);
            }
            default -> {
                BigDecimal capital = asset.getPrincipal()
                        .multiply(residualCapitalFraction, MC)
                        .setScale(MONEY_SCALE, RM);
                BigDecimal valorAsOf = calculateValueAsOf(asset, asOfDate);
                BigDecimal rendimiento = valorAsOf.subtract(capital, MC).setScale(MONEY_SCALE, RM);
                yield new CollectBreakdown(capital, rendimiento);
            }
        };
    }
}
