package com.vectis.backend.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fechas puras del ciclo de una tarjeta (cierre y vencimiento), calculadas a
 * partir del día del mes configurado. El día se acota a la longitud del mes
 * (p. ej. día 31 en febrero → 28/29).
 */
@Component
public class CardCycle {

    /** Próxima fecha de cierre en o después de {@code today}. */
    public LocalDate nextClosingDate(int closingDay, LocalDate today) {
        return nextOccurrence(closingDay, today);
    }

    /** Próxima fecha de vencimiento en o después de {@code today}. */
    public LocalDate nextDueDate(int dueDay, LocalDate today) {
        return nextOccurrence(dueDay, today);
    }

    /** Clave de mes "YYYY-MM" de una fecha. */
    public String monthKey(LocalDate date) {
        return YearMonth.from(date).toString();
    }

    private LocalDate nextOccurrence(int day, LocalDate today) {
        LocalDate candidate = atDayClamped(YearMonth.from(today), day);
        if (candidate.isBefore(today)) {
            candidate = atDayClamped(YearMonth.from(today).plusMonths(1), day);
        }
        return candidate;
    }

    private LocalDate atDayClamped(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
