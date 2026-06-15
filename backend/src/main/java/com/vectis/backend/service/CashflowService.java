package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.*;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.repository.TransactionRepository.CategorySummaryProjection;
import com.vectis.backend.repository.TransactionRepository.NetMovementProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashflowService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;
    private static final Locale LOCALE_AR = Locale.of("es", "AR");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;

    public CashflowResponse getCashflow(User user, int year, int month) {
        UUID userId = user.getId();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        LocalDate today    = LocalDate.now();

        LocalDate currentMonthStart = today.withDayOfMonth(1);
        boolean isFuture  = firstDay.isAfter(currentMonthStart);
        boolean isCurrent = firstDay.equals(currentMonthStart);
        String status = isFuture ? "proyectado" : isCurrent ? "curso" : "cerrado";
        boolean isProjection = isFuture;

        // ── Cuentas incluidas en cashflow ─────────────────────────────────────
        List<Account> cashflowAccounts = accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId);

        // ── Opening balance (último día del mes anterior) ─────────────────────
        LocalDate openingDate = firstDay.minusDays(1);
        CashflowBalanceSection openingBalance = buildBalanceSection(cashflowAccounts, userId, openingDate);

        // ── Closing balance (último día del mes o hoy si es mes actual) ───────
        LocalDate closingDate = isCurrent ? today : lastDay;
        CashflowBalanceSection closingBalance = buildBalanceSection(cashflowAccounts, userId, closingDate);

        // ── Income y Expenses ─────────────────────────────────────────────────
        CashflowFlowSection income;
        CashflowFlowSection expenses;

        if (isProjection) {
            income   = buildProjectedSection(userId, "INCOME",  today);
            expenses = buildProjectedSection(userId, "EXPENSE", today);
        } else {
            // Presupuestos del mes
            Map<UUID, BigDecimal> budgets = loadBudgets(userId, firstDay);

            List<CategorySummaryProjection> incomeRows  = transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay);
            List<CategorySummaryProjection> expenseRows = transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay);

            BigDecimal totalIncome  = sumProjections(incomeRows);
            BigDecimal totalExpense = sumProjections(expenseRows);

            income   = buildFlowSection(incomeRows,  totalIncome,  Collections.emptyMap());
            expenses = buildFlowSection(expenseRows, totalExpense, budgets);
        }

        // ── Pre-investment subtotal ───────────────────────────────────────────
        BigDecimal openingTotal     = openingBalance.total();
        BigDecimal operativeResult  = income.total().subtract(expenses.total(), MC);
        BigDecimal preBalance       = openingTotal.add(operativeResult, MC);
        BigDecimal savingRate       = income.total().compareTo(BigDecimal.ZERO) != 0
                ? operativeResult.divide(income.total(), MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                : BigDecimal.ZERO.setScale(2, RM);
        CashflowSubtotal preInvestmentBalance = new CashflowSubtotal(
                preBalance.setScale(4, RM),
                operativeResult.setScale(4, RM),
                savingRate);

        // ── Inversiones (categorías cuyo nombre sea "Inversiones") ───────────
        CashflowInvestmentSection investmentSection = buildInvestmentSection(expenses, preBalance);

        // ── Period labels ─────────────────────────────────────────────────────
        String periodLabel = buildPeriodLabel(firstDay);
        String monthShort  = buildMonthShort(firstDay);

        return new CashflowResponse(
                year, month, periodLabel, monthShort,
                status, isProjection,
                openingBalance,
                income,
                expenses,
                preInvestmentBalance,
                investmentSection,
                closingBalance
        );
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private CashflowBalanceSection buildBalanceSection(List<Account> accounts, UUID userId, LocalDate date) {
        if (accounts.isEmpty()) {
            return new CashflowBalanceSection(BigDecimal.ZERO.setScale(4, RM), Collections.emptyList());
        }
        List<UUID> ids = accounts.stream().map(Account::getId).toList();
        Map<UUID, BigDecimal> netMap = transactionRepository.netMovementsForAccounts(userId, ids, date)
                .stream()
                .collect(Collectors.toMap(NetMovementProjection::getAccountId,
                                          NetMovementProjection::getNetAmount));

        List<CashflowAccountBalance> rows = accounts.stream()
                .map(a -> {
                    BigDecimal net      = netMap.getOrDefault(a.getId(), BigDecimal.ZERO);
                    BigDecimal computed = a.getBalance().add(net, MC).setScale(4, RM);
                    return new CashflowAccountBalance(a.getId().toString(), a.getName(), a.getCcy(), computed);
                })
                .toList();

        BigDecimal total = rows.stream()
                .map(CashflowAccountBalance::balance)
                .reduce(BigDecimal.ZERO, (x, y) -> x.add(y, MC))
                .setScale(4, RM);

        return new CashflowBalanceSection(total, rows);
    }

    private CashflowFlowSection buildFlowSection(List<CategorySummaryProjection> rows,
                                                  BigDecimal total,
                                                  Map<UUID, BigDecimal> budgets) {
        List<CashflowCategoryRow> categoryRows = rows.stream()
                .map(row -> {
                    BigDecimal pctOfTotal = total.compareTo(BigDecimal.ZERO) != 0
                            ? row.getTotalAmount().divide(total, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                            : BigDecimal.ZERO.setScale(2, RM);

                    BigDecimal budgeted    = budgets.get(row.getCategoryId());
                    BigDecimal pctOfBudget = null;
                    if (budgeted != null && budgeted.compareTo(BigDecimal.ZERO) != 0) {
                        pctOfBudget = row.getTotalAmount().divide(budgeted, MC)
                                .multiply(BigDecimal.valueOf(100), MC)
                                .setScale(2, RM);
                    }

                    return new CashflowCategoryRow(
                            row.getCategoryId() != null ? row.getCategoryId().toString() : null,
                            row.getCategoryName(),
                            row.getCategoryIcon(),
                            row.getCategoryColor(),
                            row.getTotalAmount().setScale(4, RM),
                            pctOfTotal,
                            budgeted != null ? budgeted.setScale(4, RM) : null,
                            pctOfBudget
                    );
                })
                .toList();

        return new CashflowFlowSection(total.setScale(4, RM), categoryRows);
    }

    /**
     * Para meses futuros: promedia los últimos 3 meses cerrados disponibles.
     * Si no hay historial, devuelve sección vacía con total = 0.
     */
    private CashflowFlowSection buildProjectedSection(UUID userId, String type, LocalDate today) {
        // Recopilar hasta 3 meses cerrados anteriores al mes actual
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        List<CashflowFlowSection> historicalSections = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            LocalDate hFirst = currentMonthStart.minusMonths(i);
            LocalDate hLast  = hFirst.withDayOfMonth(hFirst.lengthOfMonth());
            List<CategorySummaryProjection> rows = transactionRepository.groupByCategory(userId, type, hFirst, hLast);
            if (!rows.isEmpty()) {
                BigDecimal total = sumProjections(rows);
                historicalSections.add(buildFlowSection(rows, total, Collections.emptyMap()));
            }
        }

        if (historicalSections.isEmpty()) {
            return new CashflowFlowSection(BigDecimal.ZERO.setScale(4, RM), Collections.emptyList());
        }

        // Promediar totales
        BigDecimal avgTotal = historicalSections.stream()
                .map(CashflowFlowSection::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historicalSections.size()), MC)
                .setScale(4, RM);

        // Tomar las categorías del mes histórico más reciente como referencia
        List<CashflowCategoryRow> refRows = historicalSections.get(0).byCategory();

        // Escalar los montos de cada categoría proporcionalmente al promedio
        BigDecimal refTotal = historicalSections.get(0).total();
        BigDecimal scaleFactor = refTotal.compareTo(BigDecimal.ZERO) != 0
                ? avgTotal.divide(refTotal, MC)
                : BigDecimal.ONE;

        List<CashflowCategoryRow> projectedRows = refRows.stream()
                .map(row -> new CashflowCategoryRow(
                        row.categoryId(),
                        row.name(),
                        row.icon(),
                        row.color(),
                        row.amount().multiply(scaleFactor, MC).setScale(4, RM),
                        row.pctOfTotal(),
                        null,
                        null
                ))
                .toList();

        return new CashflowFlowSection(avgTotal, projectedRows);
    }

    private CashflowInvestmentSection buildInvestmentSection(CashflowFlowSection expenses,
                                                              BigDecimal preBalance) {
        // Filtrar filas de egresos cuya categoría se llame "Inversiones" (case-insensitive)
        List<CashflowInvestmentRow> instruments = expenses.byCategory().stream()
                .filter(row -> "inversiones".equalsIgnoreCase(row.name()))
                .map(row -> new CashflowInvestmentRow(
                        row.name(),
                        row.icon(),
                        row.color(),
                        row.amount(),
                        null  // TEA: null en MVP
                ))
                .toList();

        BigDecimal total = instruments.stream()
                .map(CashflowInvestmentRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RM);

        BigDecimal pctOfPreBalance = null;
        if (preBalance.compareTo(BigDecimal.ZERO) != 0) {
            pctOfPreBalance = total.divide(preBalance, MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(2, RM);
        }

        return new CashflowInvestmentSection(total, pctOfPreBalance, instruments);
    }

    private Map<UUID, BigDecimal> loadBudgets(UUID userId, LocalDate firstDay) {
        return categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay)
                .stream()
                .collect(Collectors.toMap(
                        cb -> cb.getCategory().getId(),
                        CategoryBudget::getAmount,
                        (a, b) -> a  // en caso de duplicado, tomar el primero
                ));
    }

    private BigDecimal sumProjections(List<CategorySummaryProjection> rows) {
        return rows.stream()
                .map(CategorySummaryProjection::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildPeriodLabel(LocalDate date) {
        String raw = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_AR).format(date);
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private String buildMonthShort(LocalDate date) {
        return DateTimeFormatter.ofPattern("MMM", LOCALE_AR).format(date);
    }
}
