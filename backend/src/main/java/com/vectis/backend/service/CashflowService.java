package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.*;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.MonthPeriodRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
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
    private final ExchangeRateRepository exchangeRateRepository;
    private final MonthPeriodService monthPeriodService;
    private final MonthPeriodRepository monthPeriodRepository;
    private final RecurringMovementRepository recurringMovementRepository;

    @Transactional // override class-level readOnly=true so getStatus() writes (MonthPeriod + recurring) commit
    public CashflowResponse getCashflow(User user, int year, int month) {
        UUID userId = user.getId();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        LocalDate today    = LocalDate.now();

        // ── Status via MonthPeriodService ─────────────────────────────────────
        String status = monthPeriodService.getStatus(user, year, month, today);
        boolean isProjection = "proyectado".equals(status);

        // ── Cuentas incluidas en cashflow ─────────────────────────────────────
        List<Account> cashflowAccounts = accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId);

        // ── Opening balance (último día del mes anterior) ─────────────────────
        LocalDate openingDate = firstDay.minusDays(1);
        CashflowBalanceSection openingBalance;
        if (isProjection) {
            LocalDate prevFirst = firstDay.minusMonths(1);
            int prevYear  = prevFirst.getYear();
            int prevMonth = prevFirst.getMonthValue();
            String prevStatus = monthPeriodService.getStatus(user, prevYear, prevMonth, today);
            if ("proyectado".equals(prevStatus)) {
                CashflowResponse prevCashflow = getCashflow(user, prevYear, prevMonth);
                openingBalance = new CashflowBalanceSection(
                        prevCashflow.getClosingBalance().total(), Collections.emptyList());
            } else {
                openingBalance = buildBalanceSection(cashflowAccounts, userId, openingDate);
            }
        } else {
            openingBalance = buildBalanceSection(cashflowAccounts, userId, openingDate);
        }

        // ── Closing balance (computed after preBalance for projected months) ───
        boolean isCurrent = "curso".equals(status);
        LocalDate closingDate = isCurrent ? today : lastDay;
        CashflowBalanceSection closingBalance = isProjection
                ? null   // placeholder — overridden below after preBalance is known
                : buildBalanceSection(cashflowAccounts, userId, closingDate);

        // ── Income y Expenses ─────────────────────────────────────────────────
        CashflowFlowSection income;
        CashflowFlowSection expenses;

        if (isProjection) {
            income   = buildProjectedSection(userId, TransactionType.INCOME,  year, month);
            expenses = buildProjectedSection(userId, TransactionType.EXPENSE, year, month);
        } else {
            List<CategoryBudget> allBudgets = categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay);
            Map<UUID, BigDecimal> incomeBudgets = allBudgets.stream()
                    .filter(cb -> cb.getCategory().getType() == CategoryType.INCOME)
                    .collect(Collectors.toMap(cb -> cb.getCategory().getId(), CategoryBudget::getAmount, (a, b) -> a));
            Map<UUID, BigDecimal> expenseBudgets = allBudgets.stream()
                    .filter(cb -> cb.getCategory().getType() == CategoryType.EXPENSE)
                    .collect(Collectors.toMap(cb -> cb.getCategory().getId(), CategoryBudget::getAmount, (a, b) -> a));

            List<CategorySummaryProjection> incomeRows  = transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay);
            List<CategorySummaryProjection> expenseRows = transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay);

            BigDecimal totalIncome  = sumProjections(incomeRows);
            BigDecimal totalExpense = sumProjections(expenseRows);

            income   = buildFlowSection(incomeRows,  totalIncome,  incomeBudgets);
            expenses = buildFlowSection(expenseRows, totalExpense, expenseBudgets);
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

        // ── Projected closing: derive from preBalance (account query excluded projected txns) ──
        if (isProjection) {
            closingBalance = new CashflowBalanceSection(preBalance.setScale(4, RM), Collections.emptyList());
        }

        // ── Inversiones ───────────────────────────────────────────────────────
        CashflowInvestmentSection investmentSection = buildInvestmentSection(expenses, preBalance);

        // ── recurringMaterialized flag ────────────────────────────────────────
        boolean recurringMaterialized = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(userId, year, month)
                .map(mp -> mp.getRecurringMaterializedAt() != null)
                .orElse(false);

        // ── Period labels ─────────────────────────────────────────────────────
        String periodLabel = buildPeriodLabel(firstDay);
        String monthShort  = buildMonthShort(firstDay);

        CashflowResponse response = CashflowResponse.builder()
                .year(year)
                .month(month)
                .periodLabel(periodLabel)
                .monthShort(monthShort)
                .status(status)
                .isProjection(isProjection)
                .recurringMaterialized(recurringMaterialized)
                .openingBalance(openingBalance)
                .income(income)
                .expenses(expenses)
                .preInvestmentBalance(preInvestmentBalance)
                .investments(investmentSection)
                .closingBalance(closingBalance)
                .build();

        // ── Liquidity deficit ─────────────────────────────────────────────────
        BigDecimal preInv = preInvestmentBalance.balance();
        response.setHasLiquidityDeficit(preInv.compareTo(BigDecimal.ZERO) < 0);
        response.setLiquidityDeficit(preInv.abs().setScale(4, RM).toPlainString());

        // ── needsConfirmation ─────────────────────────────────────────────────
        boolean isFuture = year > today.getYear()
                || (year == today.getYear() && month > today.getMonthValue());
        boolean hasProjectedRecord = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(userId, year, month)
                .map(mp -> "PROJECTED".equals(mp.getStatus()))
                .orElse(false);
        response.setNeedsConfirmation(isFuture && !hasProjectedRecord);

        // ── Cotizacion OFICIAL al cierre del período ──────────────────────────
        String oficialRateAtPeriod = exchangeRateRepository
                .findByRateTypeAndRateDate("OFICIAL", lastDay)
                .or(() -> exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("OFICIAL"))
                .map(r -> r.getSell().toPlainString())
                .orElse(null);
        response.setOficialRateAtPeriod(oficialRateAtPeriod);

        return response;
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

        BigDecimal totalBudgeted = budgets.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RM);

        return new CashflowFlowSection(total.setScale(4, RM), totalBudgeted, categoryRows);
    }

    /**
     * Para meses proyectados: combina transacciones ya registradas, recurrentes activos
     * (para categorías sin registro aún) y presupuesto residual ("Otros").
     * Prioridad: registrado > recurrente template > presupuesto.
     */
    private CashflowFlowSection buildProjectedSection(UUID userId, TransactionType type, int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        CategoryType catType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;

        // ── 1. Transacciones ya registradas para este mes futuro ──────────────
        List<CategorySummaryProjection> registeredRows =
                transactionRepository.groupByCategory(userId, type, firstDay, lastDay);
        Set<UUID> registeredCatIds = registeredRows.stream()
                .map(CategorySummaryProjection::getCategoryId)
                .collect(Collectors.toSet());
        BigDecimal registeredTotal = sumProjections(registeredRows);

        // ── 2. Recurrentes para categorías no cubiertas por registradas ────────
        Map<UUID, BigDecimal> recurringByCategory = new LinkedHashMap<>();
        Map<UUID, RecurringMovement> catMeta = new LinkedHashMap<>();
        for (RecurringMovement rm : recurringMovementRepository
                .findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId)) {
            if (!type.name().equals(rm.getType())) continue;
            UUID catId = rm.getCategory() != null ? rm.getCategory().getId() : null;
            if (!registeredCatIds.contains(catId)) {
                recurringByCategory.merge(catId, rm.getAmount().abs(), BigDecimal::add);
                catMeta.putIfAbsent(catId, rm);
            }
        }
        BigDecimal recurringOnlyTotal = recurringByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── 3. Presupuesto ────────────────────────────────────────────────────
        List<CategoryBudget> budgets = categoryBudgetRepository
                .findLatestPerCategoryOnOrBefore(userId, firstDay)
                .stream()
                .filter(cb -> cb.getCategory().getType() == catType)
                .toList();
        Map<UUID, BigDecimal> catBudgetMap = budgets.stream()
                .collect(Collectors.toMap(cb -> cb.getCategory().getId(),
                                          CategoryBudget::getAmount, (a, b) -> a));
        BigDecimal budgetTotal = budgets.stream()
                .map(CategoryBudget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasBudget = budgetTotal.compareTo(BigDecimal.ZERO) > 0;

        // ── 2b. Categorías solo presupuestadas (sin recurrente ni registrada) ──
        // En lugar de agrupar en una fila genérica "Otros", las exponemos individualmente.
        Set<UUID> coveredCatIds = new HashSet<>(registeredCatIds);
        coveredCatIds.addAll(recurringByCategory.keySet());
        Map<UUID, CategoryBudget> budgetOnlyMap = new LinkedHashMap<>();
        for (CategoryBudget cb : budgets) {
            UUID catId = cb.getCategory().getId();
            if (!coveredCatIds.contains(catId)) {
                budgetOnlyMap.put(catId, cb);
            }
        }
        BigDecimal budgetOnlyTotal = budgetOnlyMap.values().stream()
                .map(CategoryBudget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // sectionTotal = suma de lo que se va a mostrar en filas (recurrente real + presupuestado individual).
        // No se usa max(budget, effective) porque los presupuestados ya están en effectiveTotal como filas propias.
        BigDecimal effectiveTotal = registeredTotal.add(recurringOnlyTotal).add(budgetOnlyTotal);
        BigDecimal sectionTotal = effectiveTotal;

        if (sectionTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new CashflowFlowSection(BigDecimal.ZERO.setScale(4, RM),
                                           budgetTotal.setScale(4, RM), Collections.emptyList());
        }

        List<CashflowCategoryRow> rows = new ArrayList<>();

        // ── 4a. Filas de transacciones registradas ────────────────────────────
        for (CategorySummaryProjection proj : registeredRows) {
            BigDecimal amt = proj.getTotalAmount().setScale(4, RM);
            BigDecimal pctOfTotal = amt.divide(sectionTotal, MC)
                    .multiply(BigDecimal.valueOf(100), MC).setScale(2, RM);
            UUID catId = proj.getCategoryId();
            BigDecimal catBudget = catId != null ? catBudgetMap.get(catId) : null;
            BigDecimal pctOfBudget = (catBudget != null && catBudget.compareTo(BigDecimal.ZERO) != 0)
                    ? amt.divide(catBudget, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : null;
            rows.add(new CashflowCategoryRow(
                    catId != null ? catId.toString() : null,
                    proj.getCategoryName(), proj.getCategoryIcon(), proj.getCategoryColor(),
                    amt, pctOfTotal,
                    catBudget != null ? catBudget.setScale(4, RM) : null,
                    pctOfBudget
            ));
        }

        // ── 4b. Filas de recurrentes sin registrar (con comparación de presupuesto) ──
        for (Map.Entry<UUID, BigDecimal> entry : recurringByCategory.entrySet()) {
            UUID catId = entry.getKey();
            BigDecimal amt = entry.getValue().setScale(4, RM);
            RecurringMovement meta = catMeta.get(catId);
            BigDecimal pctOfTotal = amt.divide(sectionTotal, MC)
                    .multiply(BigDecimal.valueOf(100), MC).setScale(2, RM);
            BigDecimal catBudget = catId != null ? catBudgetMap.get(catId) : null;
            BigDecimal pctOfBudget = (catBudget != null && catBudget.compareTo(BigDecimal.ZERO) != 0)
                    ? amt.divide(catBudget, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : null;
            rows.add(new CashflowCategoryRow(
                    catId != null ? catId.toString() : null,
                    meta != null && meta.getCategory() != null ? meta.getCategory().getName() : "Sin categoría",
                    meta != null && meta.getCategory() != null ? meta.getCategory().getIcon() : "circle",
                    meta != null && meta.getCategory() != null ? meta.getCategory().getColor() : "#85948f",
                    amt, pctOfTotal,
                    catBudget != null ? catBudget.setScale(4, RM) : null,
                    pctOfBudget
            ));
        }

        // ── 4c. Filas solo presupuestadas (presupuesto como monto proyectado) ──
        for (Map.Entry<UUID, CategoryBudget> entry : budgetOnlyMap.entrySet()) {
            UUID catId = entry.getKey();
            CategoryBudget cb = entry.getValue();
            BigDecimal amt = cb.getAmount().setScale(4, RM);
            BigDecimal pctOfTotal = amt.divide(sectionTotal, MC)
                    .multiply(BigDecimal.valueOf(100), MC).setScale(2, RM);
            rows.add(new CashflowCategoryRow(
                    catId.toString(),
                    cb.getCategory().getName(),
                    cb.getCategory().getIcon(),
                    cb.getCategory().getColor(),
                    amt, pctOfTotal,
                    amt,  // budgeted = mismo valor (proyección = presupuesto completo)
                    new BigDecimal("100.00").setScale(2, RM)
            ));
        }

        BigDecimal totalBudgeted = hasBudget ? budgetTotal.setScale(4, RM) : BigDecimal.ZERO.setScale(4, RM);
        return new CashflowFlowSection(sectionTotal.setScale(4, RM), totalBudgeted, rows);
    }

    private CashflowInvestmentSection buildInvestmentSection(CashflowFlowSection expenses,
                                                              BigDecimal preBalance) {
        List<CashflowInvestmentRow> instruments = expenses.byCategory().stream()
                .filter(row -> "inversiones".equalsIgnoreCase(row.name()))
                .map(row -> new CashflowInvestmentRow(row.name(), row.icon(), row.color(), row.amount(), null))
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
