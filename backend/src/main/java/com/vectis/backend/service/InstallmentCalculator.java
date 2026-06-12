package com.vectis.backend.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica pura para dividir una compra diferida en N cuotas.
 *
 * <p>Reglas de negocio (ciclo de tarjeta):</p>
 * <ul>
 *   <li>El resumen cierra el primer {@code closingDay} en o después de la compra:
 *       si el día de compra es {@code <= closingDay}, cierra ese mismo mes; si es
 *       posterior, cierra el mes siguiente.</li>
 *   <li>La cuota {@code k} (1..N) vence el {@code dueDay} del mes
 *       {@code (mes de cierre + k)}, acotando el día a la longitud del mes.</li>
 *   <li>Los montos se reparten con {@link RoundingMode#HALF_EVEN}; la última cuota
 *       absorbe el remanente para que la suma sea exactamente el total.</li>
 * </ul>
 */
@Component
public class InstallmentCalculator {

    /** Una cuota calculada: número (1..N), fecha de vencimiento y monto. */
    public record Installment(int number, LocalDate dueDate, BigDecimal amount) {}

    public List<Installment> split(BigDecimal total, LocalDate purchaseDate,
                                   int closingDay, int dueDay, int installments) {
        if (installments < 1) {
            throw new IllegalArgumentException("La cantidad de cuotas debe ser al menos 1");
        }

        // Mes en que cierra el resumen que contiene la compra.
        YearMonth closingMonth = YearMonth.from(purchaseDate);
        if (purchaseDate.getDayOfMonth() > closingDay) {
            closingMonth = closingMonth.plusMonths(1);
        }

        // Reparto de montos: base con HALF_EVEN; la última cuota absorbe el resto.
        BigDecimal base = total.divide(BigDecimal.valueOf(installments), 4, RoundingMode.HALF_EVEN);
        BigDecimal accumulated = base.multiply(BigDecimal.valueOf(installments - 1L));
        BigDecimal last = total.subtract(accumulated);

        List<Installment> result = new ArrayList<>(installments);
        for (int k = 1; k <= installments; k++) {
            YearMonth dueMonth = closingMonth.plusMonths(k);
            int day = Math.min(dueDay, dueMonth.lengthOfMonth());
            LocalDate dueDate = dueMonth.atDay(day);
            BigDecimal amount = (k == installments) ? last : base;
            result.add(new Installment(k, dueDate, amount));
        }
        return result;
    }
}
