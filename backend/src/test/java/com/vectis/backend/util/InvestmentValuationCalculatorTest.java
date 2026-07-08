package com.vectis.backend.util;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvestmentValuationCalculator")
class InvestmentValuationCalculatorTest {

    private InvestmentAsset.InvestmentAssetBuilder baseAsset(InvestmentAssetType type) {
        return InvestmentAsset.builder()
                .name("Activo Test").type(type).currency("USD")
                .principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .movements(new ArrayList<>())
                .valuations(new ArrayList<>());
    }

    private InvestmentMovement movement(LocalDate date, InvestmentMovementType type, BigDecimal amount, BigDecimal units) {
        return InvestmentMovement.builder()
                .movementDate(date).type(type).amount(amount).units(units).build();
    }

    // ─── isCuotaparteFamily ──────────────────────────────────────────────────

    @Test
    @DisplayName("isCuotaparteFamily: true para FCI_CUOTAPARTES/LETRA/BONO/ON, false para FCI/PLAZO_FIJO")
    void isCuotaparteFamily_classifiesTypesCorrectly() {
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.FCI_CUOTAPARTES)).isTrue();
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.LETRA)).isTrue();
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.BONO)).isTrue();
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.ON)).isTrue();
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.FCI)).isFalse();
        assertThat(InvestmentValuationCalculator.isCuotaparteFamily(InvestmentAssetType.PLAZO_FIJO)).isFalse();
    }

    // ─── unitsHeldAsOf ───────────────────────────────────────────────────────

    @Test
    @DisplayName("unitsHeldAsOf: sólo considera movimientos con fecha <= asOfDate (compra posterior no infla el pasado)")
    void unitsHeldAsOf_onlyCountsMovementsOnOrBeforeDate() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        // Compra adicional POSTERIOR al primer corte de pago (2026-07-09)
        asset.getMovements().add(movement(LocalDate.of(2026, 8, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("50000.00"), new BigDecimal("50")));

        BigDecimal heldAtCutting = InvestmentValuationCalculator.unitsHeldAsOf(asset, LocalDate.of(2026, 7, 9));
        BigDecimal heldToday     = InvestmentValuationCalculator.unitsHeldAsOf(asset, LocalDate.of(2026, 12, 1));

        assertThat(heldAtCutting).isEqualByComparingTo("100"); // la compra de agosto no cuenta todavía
        assertThat(heldToday).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("unitsHeldAsOf: descuenta RESCATE hasta la fecha dada")
    void unitsHeldAsOf_subtractsRescateUpToDate() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        asset.getMovements().add(movement(LocalDate.of(2026, 3, 1), InvestmentMovementType.RESCATE,
                new BigDecimal("30000.00"), new BigDecimal("30")));

        BigDecimal held = InvestmentValuationCalculator.unitsHeldAsOf(asset, LocalDate.of(2026, 7, 9));

        assertThat(held).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("unitsHeldAsOf: sin movimientos devuelve 0")
    void unitsHeldAsOf_returnsZero_whenNoMovements() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();

        assertThat(InvestmentValuationCalculator.unitsHeldAsOf(asset, LocalDate.of(2026, 7, 9)))
                .isEqualByComparingTo("0");
    }

    // ─── calculateCurrentValue / calculateValueAsOf ─────────────────────────

    @Test
    @DisplayName("calculateCurrentValue: PLAZO_FIJO y FCI devuelven principal tal cual")
    void calculateCurrentValue_returnsPrincipalForNonCuotaparteTypes() {
        InvestmentAsset plazoFijo = baseAsset(InvestmentAssetType.PLAZO_FIJO).build();
        InvestmentAsset fci = baseAsset(InvestmentAssetType.FCI).build();

        assertThat(InvestmentValuationCalculator.calculateCurrentValue(plazoFijo))
                .isEqualByComparingTo(plazoFijo.getPrincipal());
        assertThat(InvestmentValuationCalculator.calculateCurrentValue(fci))
                .isEqualByComparingTo(fci.getPrincipal());
    }

    @Test
    @DisplayName("calculateCurrentValue: familia cuotapartes usa tenencia × último precio")
    void calculateCurrentValue_usesLatestPriceForCuotaparteFamily() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        asset.getValuations().add(InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 6, 1)).pricePerUnit(new BigDecimal("1050.0000")).source("PPI").build());

        assertThat(InvestmentValuationCalculator.calculateCurrentValue(asset)).isEqualByComparingTo("105000.0000");
    }

    @Test
    @DisplayName("calculateValueAsOf: usa la última valuación con fecha <= asOfDate, no la global")
    void calculateValueAsOf_usesLatestValuationOnOrBeforeDate() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        asset.getValuations().add(InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 3, 1)).pricePerUnit(new BigDecimal("1000.0000")).source("PPI").build());
        asset.getValuations().add(InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 6, 1)).pricePerUnit(new BigDecimal("1050.0000")).source("PPI").build());

        BigDecimal valueInApril = InvestmentValuationCalculator.calculateValueAsOf(asset, LocalDate.of(2026, 4, 1));

        assertThat(valueInApril).isEqualByComparingTo("100000.0000"); // toma la de marzo, no la de junio
    }

    // ─── calculateCollectBreakdown — fracción residual (anti doble-contabilización) ──

    @Test
    @DisplayName("calculateCollectBreakdown de 2 args delega con fracción 1 (nada amortizado)")
    void calculateCollectBreakdown_twoArgOverload_delegatesWithFractionOne() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        asset.getValuations().add(InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 6, 1)).pricePerUnit(new BigDecimal("1050.0000")).source("PPI").build());

        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, LocalDate.of(2026, 7, 1));

        assertThat(breakdown.capital()).isEqualByComparingTo("100000.0000");
        assertThat(breakdown.rendimiento()).isEqualByComparingTo("5000.0000"); // 105000 - 100000
    }

    @Test
    @DisplayName("calculateCollectBreakdown con fracción residual < 1 descuenta el capital ya amortizado")
    void calculateCollectBreakdown_withResidualFraction_discountsAmortizedCapital() {
        // Escenario: compró 100 nominales por 100000, cobró una amortización de 8% (8 por 100) vía
        // el calendario de InvestmentPaymentService. La fracción residual pasada acá es 1 - 8/100 = 0.92.
        InvestmentAsset asset = baseAsset(InvestmentAssetType.BONO).build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), new BigDecimal("100")));
        asset.getValuations().add(InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 8, 1)).pricePerUnit(new BigDecimal("920.0000")).source("PPI").build());

        BigDecimal residualFraction = BigDecimal.ONE.subtract(new BigDecimal("8").divide(BigDecimal.valueOf(100)));
        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, LocalDate.of(2026, 9, 1), residualFraction);

        // capital = 100000 * 0.92 = 92000; valorAsOf = 100 * 920 = 92000; rendimiento = 0
        assertThat(breakdown.capital()).isEqualByComparingTo("92000.0000");
        assertThat(breakdown.rendimiento()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.total()).isEqualByComparingTo("92000.0000");
    }

    @Test
    @DisplayName("calculateCollectBreakdown: la fracción residual NO se aplica a PLAZO_FIJO")
    void calculateCollectBreakdown_residualFraction_notAppliedToPlazoFijo() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.PLAZO_FIJO)
                .tna(new BigDecimal("36.5000")).build();

        InvestmentValuationCalculator.CollectBreakdown breakdown = InvestmentValuationCalculator
                .calculateCollectBreakdown(asset, LocalDate.of(2026, 1, 11), new BigDecimal("0.5"));

        // capital ignora la fracción (sólo aplica a la rama default/cuotapartes)
        assertThat(breakdown.capital()).isEqualByComparingTo(asset.getPrincipal());
    }

    @Test
    @DisplayName("calculateCollectBreakdown PLAZO_FIJO: interés simple por TNA desde la compra")
    void calculateCollectBreakdown_plazoFijo_simpleInterest() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.PLAZO_FIJO)
                .principal(new BigDecimal("100000.0000"))
                .tna(new BigDecimal("36.5000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .build();

        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, LocalDate.of(2026, 2, 1));

        // 100000 * (36.5/100) * 31/365 = 3100.00
        assertThat(breakdown.rendimiento()).isEqualByComparingTo("3100.0000");
        assertThat(breakdown.capital()).isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("calculateCollectBreakdown FCI: rendimiento = capitalizado por REVALUO + interés adicional devengado")
    void calculateCollectBreakdown_fci_capitalizedPlusAccrued() {
        InvestmentAsset asset = baseAsset(InvestmentAssetType.FCI)
                .principal(new BigDecimal("103000.0000")) // 100000 suscripto + 3000 ya capitalizado por revalúo
                .tna(new BigDecimal("36.5000"))
                .build();
        asset.getMovements().add(movement(LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"), null));
        asset.getMovements().add(movement(LocalDate.of(2026, 2, 1), InvestmentMovementType.REVALUO,
                new BigDecimal("103000.00"), null));

        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, LocalDate.of(2026, 2, 1));

        assertThat(breakdown.capital()).isEqualByComparingTo("100000.0000");
        assertThat(breakdown.rendimiento()).isEqualByComparingTo("3000.0000"); // sin días adicionales devengados
    }
}
