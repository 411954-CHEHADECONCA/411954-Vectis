package com.vectis.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardCycle")
class CardCycleTest {

    private final CardCycle cardCycle = new CardCycle();

    @Test
    @DisplayName("nextDueDate devuelve el día de este mes si aún no pasó")
    void nextDueDate_sameMonthWhenNotPassed() {
        LocalDate due = cardCycle.nextDueDate(15, LocalDate.of(2026, 6, 10));
        assertThat(due).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("nextDueDate salta al mes siguiente si el día ya pasó")
    void nextDueDate_nextMonthWhenPassed() {
        LocalDate due = cardCycle.nextDueDate(5, LocalDate.of(2026, 6, 10));
        assertThat(due).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    @DisplayName("nextClosingDate acota el día a la longitud del mes (31 en febrero → 28)")
    void nextClosingDate_clampsToMonthLength() {
        LocalDate closing = cardCycle.nextClosingDate(31, LocalDate.of(2026, 2, 1));
        assertThat(closing).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("monthKey formatea YYYY-MM")
    void monthKey_formats() {
        assertThat(cardCycle.monthKey(LocalDate.of(2026, 6, 15))).isEqualTo("2026-06");
    }
}
