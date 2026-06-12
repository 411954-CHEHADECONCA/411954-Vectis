package com.vectis.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InstallmentCalculator")
class InstallmentCalculatorTest {

    private final InstallmentCalculator calculator = new InstallmentCalculator();

    @Test
    @DisplayName("3 cuotas de $10.000 suman exactamente el total sin perder centavos")
    void split_threeInstallments_sumsExactlyTotal() {
        BigDecimal total = new BigDecimal("10000.00");
        List<InstallmentCalculator.Installment> parts =
                calculator.split(total, LocalDate.of(2026, 4, 1), 5, 15, 3);

        assertThat(parts).hasSize(3);
        BigDecimal sum = parts.stream()
                .map(InstallmentCalculator.Installment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(total)).isZero();
    }

    @Test
    @DisplayName("Monto no divisible: $10.000 / 3 reparte 3333.3333 y la última absorbe el resto")
    void split_indivisible_lastAbsorbsRemainder() {
        BigDecimal total = new BigDecimal("10000.0000");
        List<InstallmentCalculator.Installment> parts =
                calculator.split(total, LocalDate.of(2026, 4, 1), 5, 15, 3);

        assertThat(parts.get(0).amount()).isEqualByComparingTo("3333.3333");
        assertThat(parts.get(1).amount()).isEqualByComparingTo("3333.3333");
        assertThat(parts.get(2).amount()).isEqualByComparingTo("3333.3334");
    }

    @Test
    @DisplayName("Compra el día del cierre: la primera cuota vence el mes siguiente")
    void split_purchaseOnClosingDay_firstDueNextMonth() {
        // Compra 5/4, cierre día 5 → cierra en abril; cuota 1 vence el dueDay de mayo.
        List<InstallmentCalculator.Installment> parts =
                calculator.split(new BigDecimal("6000"), LocalDate.of(2026, 4, 5), 5, 15, 3);

        assertThat(parts.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    @DisplayName("Compra posterior al cierre: la primera cuota vence dos meses después")
    void split_purchaseAfterClosing_firstDueTwoMonthsLater() {
        // Compra 6/4 con cierre día 5 → cierra en mayo; cuota 1 vence el dueDay de junio.
        List<InstallmentCalculator.Installment> parts =
                calculator.split(new BigDecimal("6000"), LocalDate.of(2026, 4, 6), 5, 15, 3);

        assertThat(parts.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("N=6 genera 6 cuotas con vencimientos mensuales correlativos")
    void split_sixInstallments_consecutiveMonthlyDueDates() {
        List<InstallmentCalculator.Installment> parts =
                calculator.split(new BigDecimal("60000"), LocalDate.of(2026, 4, 1), 5, 15, 6);

        assertThat(parts).hasSize(6);
        assertThat(parts).extracting(InstallmentCalculator.Installment::dueDate).containsExactly(
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15));
        assertThat(parts).extracting(InstallmentCalculator.Installment::number)
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("dueDay 31 se acota a la longitud del mes (febrero → 28)")
    void split_dueDay31_clampedToMonthLength() {
        // Compra en enero, cierre día 20 → cierra en enero; cuota 1 vence en febrero.
        List<InstallmentCalculator.Installment> parts =
                calculator.split(new BigDecimal("3000"), LocalDate.of(2026, 1, 10), 20, 31, 1);

        assertThat(parts.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("installments < 1 lanza IllegalArgumentException")
    void split_zeroInstallments_throws() {
        assertThatThrownBy(() -> calculator.split(BigDecimal.TEN, LocalDate.of(2026, 4, 1), 5, 15, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
