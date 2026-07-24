package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.Transaction;
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
import java.time.YearMonth;
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
        LocalDate today = LocalDate.now();
        CashflowResponse response = buildCashflow(user, year, month, today);

        // ── Piso de navegación hacia atrás ────────────────────────────────────
        // Se calcula una sola vez acá (nivel superior), NO dentro de buildCashflow: su
        // recursión para meses proyectados descarta todo salvo closingBalance, así que
        // computarlo ahí ejecutaría la query N veces para un valor invariante por usuario.
        // Absoluto: el más antiguo entre el mes anterior al actual y el mes del primer
        // movimiento real. Sin movimientos → mes anterior (solo actual + anterior navegables).
        LocalDate earliestMovement = transactionRepository.findEarliestMovementDate(user.getId());
        YearMonth prevMonthYm = YearMonth.from(today).minusMonths(1);
        YearMonth navigableFloor = earliestMovement == null
                ? prevMonthYm
                : min(YearMonth.from(earliestMovement), prevMonthYm);
        response.setEarliestNavigableYear(navigableFloor.getYear());
        response.setEarliestNavigableMonth(navigableFloor.getMonthValue());
        return response;
    }

    private CashflowResponse buildCashflow(User user, int year, int month, LocalDate today) {
        UUID userId = user.getId();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        // ── Status via MonthPeriodService ─────────────────────────────────────
        String status = monthPeriodService.getStatus(user, year, month, today);
        boolean isProjection = "proyectado".equals(status);

        // ── Cuentas incluidas en cashflow ─────────────────────────────────────
        List<Account> cashflowAccounts = accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId);

        // ── Cotizacion OFICIAL al cierre del período ──────────────────────────
        // Se calcula temprano (antes de armar income/expenses/inversiones) porque los porcentajes de
        // esas secciones (pctOfTotal, pctOfBudget, savingRatePct, pctOfPreBalance) necesitan un valor
        // único normalizado a ARS para ser comparables — un ratio es invariante a la moneda de display
        // una vez fijado el rate, pero necesita el rate disponible antes de calcularse.
        String oficialRateAtPeriod = exchangeRateRepository
                .findByRateTypeAndRateDate("OFICIAL", lastDay)
                .or(() -> exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("OFICIAL"))
                .map(r -> r.getSell().toPlainString())
                .orElse(null);

        // ── Opening balance (último día del mes anterior) ─────────────────────
        LocalDate openingDate = firstDay.minusDays(1);
        CashflowBalanceSection openingBalance;
        if (isProjection) {
            LocalDate prevFirst = firstDay.minusMonths(1);
            int prevYear  = prevFirst.getYear();
            int prevMonth = prevFirst.getMonthValue();
            String prevStatus = monthPeriodService.getStatus(user, prevYear, prevMonth, today);
            if ("proyectado".equals(prevStatus)) {
                CashflowResponse prevCashflow = buildCashflow(user, prevYear, prevMonth, today);
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
        // Transacciones de inversión del período: se buscan acá (y no recién en buildInvestmentSection)
        // porque el RESCATE debe sumar a "Ingresos" (ver buildFlowSection) antes de calcular preBalance.
        List<Transaction> investmentTxs = Collections.emptyList();

        if (isProjection) {
            income   = buildProjectedSection(userId, TransactionType.INCOME,  year, month, oficialRateAtPeriod);
            expenses = buildProjectedSection(userId, TransactionType.EXPENSE, year, month, oficialRateAtPeriod);
        } else {
            investmentTxs = transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay);

            List<CategoryBudget> allBudgets = categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay);
            Map<UUID, BigDecimal> incomeBudgets = allBudgets.stream()
                    .filter(cb -> cb.getCategory().getType() == CategoryType.INCOME)
                    .collect(Collectors.toMap(cb -> cb.getCategory().getId(), CategoryBudget::getAmount, (a, b) -> a));
            Map<UUID, BigDecimal> expenseBudgets = allBudgets.stream()
                    .filter(cb -> cb.getCategory().getType() == CategoryType.EXPENSE)
                    .collect(Collectors.toMap(cb -> cb.getCategory().getId(), CategoryBudget::getAmount, (a, b) -> a));

            List<CategorySummaryProjection> incomeRows  = transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay);
            List<CategorySummaryProjection> expenseRows = transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay);

            // Los RESCATE y los cobros de inversión (COLLECTION_CAPITAL/COLLECTION_YIELD) quedan sin
            // categoría (van vinculados al activo, no a una categoría de usuario), así que
            // `groupByCategory` los deja afuera por el INNER JOIN implícito de `t.category.id`. Se
            // sincronizan acá como filas propias — coherente con que la cuenta ya los registró como
            // Transaction real (INCOME o, en el caso de una pérdida de mercado al cobrar, EXPENSE).
            MoneyByCcy totalRescate            = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.RESCATE, TransactionType.INCOME);
            MoneyByCcy totalCollectionCapital   = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.COLLECTION_CAPITAL, TransactionType.INCOME);
            MoneyByCcy totalCollectionYieldIn   = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.COLLECTION_YIELD, TransactionType.INCOME);
            MoneyByCcy totalCollectionYieldOut  = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.COLLECTION_YIELD, TransactionType.EXPENSE);
            // Cobro de cupones de renta/amortización de BONO/ON (calendario de pagos confirmado por el
            // usuario) — siempre INCOME, nunca generan pérdida como COLLECTION_YIELD.
            MoneyByCcy totalCouponRent          = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.COUPON_RENT, TransactionType.INCOME);
            MoneyByCcy totalAmortization        = sumInvestmentSourceTypeTotal(investmentTxs, InvestmentSourceType.AMORTIZATION, TransactionType.INCOME);

            MoneyByCcy totalInvestmentIncome  = totalRescate.add(totalCollectionCapital).add(totalCollectionYieldIn)
                    .add(totalCouponRent).add(totalAmortization);
            MoneyByCcy totalIncome  = sumProjections(incomeRows).add(totalInvestmentIncome);
            MoneyByCcy totalExpense = sumProjections(expenseRows).add(totalCollectionYieldOut);

            Map<String, MoneyByCcy> incomeInvestmentRows = new LinkedHashMap<>();
            incomeInvestmentRows.put("Rescates de inversión", totalRescate);
            incomeInvestmentRows.put("Cobro de inversión (capital)", totalCollectionCapital);
            incomeInvestmentRows.put("Cobro de inversión (rendimiento)", totalCollectionYieldIn);
            incomeInvestmentRows.put("Renta de inversión (cupones)", totalCouponRent);
            incomeInvestmentRows.put("Amortización de inversión", totalAmortization);
            Map<String, MoneyByCcy> expenseInvestmentRows = new LinkedHashMap<>();
            expenseInvestmentRows.put("Cobro de inversión (pérdida)", totalCollectionYieldOut);

            income   = buildFlowSection(incomeRows,  totalIncome,  incomeBudgets, incomeInvestmentRows, oficialRateAtPeriod);
            expenses = buildFlowSection(expenseRows, totalExpense, expenseBudgets, expenseInvestmentRows, oficialRateAtPeriod);
        }

        // ── Pre-investment subtotal ───────────────────────────────────────────
        MoneyByCcy openingTotal     = openingBalance.total();
        MoneyByCcy operativeResult  = income.total().subtract(expenses.total());
        MoneyByCcy preBalance       = openingTotal.add(operativeResult);
        BigDecimal incomeTotalArs      = toArs(income.total(), oficialRateAtPeriod);
        BigDecimal operativeResultArs  = toArs(operativeResult, oficialRateAtPeriod);
        BigDecimal savingRate       = incomeTotalArs.compareTo(BigDecimal.ZERO) != 0
                ? operativeResultArs.divide(incomeTotalArs, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
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
        CashflowInvestmentSection investmentSection = buildInvestmentSection(investmentTxs, preBalance, oficialRateAtPeriod);

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
        // Normalizado a ARS con la misma cotización del período (igual criterio que los porcentajes):
        // no hay una única moneda "correcta" para reportar un déficit compuesto de ARS+USD, así que se
        // colapsa a un valor comparable en pesos. Degrada a sólo el bucket ARS si no hay cotización.
        BigDecimal preInvArs = toArs(preInvestmentBalance.balance(), oficialRateAtPeriod);
        response.setHasLiquidityDeficit(preInvArs.compareTo(BigDecimal.ZERO) < 0);
        response.setLiquidityDeficit(preInvArs.abs().setScale(4, RM).toPlainString());

        // ── needsConfirmation ─────────────────────────────────────────────────
        boolean isFuture = year > today.getYear()
                || (year == today.getYear() && month > today.getMonthValue());
        boolean hasProjectedRecord = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(userId, year, month)
                .map(mp -> "PROJECTED".equals(mp.getStatus()))
                .orElse(false);
        response.setNeedsConfirmation(isFuture && !hasProjectedRecord);

        response.setOficialRateAtPeriod(oficialRateAtPeriod);

        return response;
    }

    private static YearMonth min(YearMonth a, YearMonth b) {
        return a.isBefore(b) ? a : b;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Convierte un {@link MoneyByCcy} a un único {@link BigDecimal} normalizado en ARS usando la
     * cotización OFICIAL (venta) del período. Usado exclusivamente para calcular porcentajes/ratios
     * (pctOfTotal, pctOfBudget, savingRatePct, pctOfPreBalance, liquidityDeficit) que necesitan un
     * valor único comparable — nunca para los totales que expone la respuesta, esos siempre quedan
     * desglosados por moneda.
     *
     * <p>Degradación documentada: si no hay cotización disponible para el período (ni exacta ni de
     * fallback), no hay forma confiable de convertir USD→ARS, así que se ignora el bucket USD y se usa
     * sólo el ARS. Es una limitación conocida y aceptada — el caso sin ninguna cotización OFICIAL
     * cargada en el sistema es marginal (la sincronización corre a diario).
     */
    private BigDecimal toArs(MoneyByCcy money, String oficialRateAtPeriod) {
        if (oficialRateAtPeriod == null) {
            return money.ars();
        }
        BigDecimal rate = new BigDecimal(oficialRateAtPeriod);
        return money.ars().add(money.usd().multiply(rate, MC), MC);
    }

    private CashflowBalanceSection buildBalanceSection(List<Account> accounts, UUID userId, LocalDate date) {
        if (accounts.isEmpty()) {
            return new CashflowBalanceSection(MoneyByCcy.zero().setScale(4, RM), Collections.emptyList());
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

        // Bimonetario: cada cuenta suma al bucket de su propia moneda, nunca a un total único ciego a
        // la moneda (ver bug de saldos USD apareciendo mezclados con ARS en el total).
        MoneyByCcy total = MoneyByCcy.zero();
        for (CashflowAccountBalance row : rows) {
            total = total.plusIn(row.ccy(), row.balance());
        }

        return new CashflowBalanceSection(total.setScale(4, RM), rows);
    }

    /** Fila de categoría ya fusionada entre las (hasta dos) filas ARS/USD que devuelve groupByCategory. */
    private record MergedCategoryTotal(UUID categoryId, String name, String icon, String color, MoneyByCcy amount) {}

    /**
     * Reconstituye un {@code MoneyByCcy} por categoría a partir de las filas por (categoría, moneda)
     * que devuelve {@link TransactionRepository#groupByCategory}. Preserva el orden de primera
     * aparición; el orden final por monto lo aplica el llamador después de convertir a ARS.
     */
    private List<MergedCategoryTotal> mergeByCategory(List<CategorySummaryProjection> rows) {
        Map<UUID, MergedCategoryTotal> merged = new LinkedHashMap<>();
        for (CategorySummaryProjection row : rows) {
            UUID catId = row.getCategoryId();
            MoneyByCcy amount = MoneyByCcy.zero().plusIn(row.getCcy(), row.getTotalAmount());
            merged.merge(catId,
                    new MergedCategoryTotal(catId, row.getCategoryName(), row.getCategoryIcon(), row.getCategoryColor(), amount),
                    (a, b) -> new MergedCategoryTotal(a.categoryId(), a.name(), a.icon(), a.color(), a.amount().add(b.amount())));
        }
        return new ArrayList<>(merged.values());
    }

    private CashflowFlowSection buildFlowSection(List<CategorySummaryProjection> rows,
                                                  MoneyByCcy total,
                                                  Map<UUID, BigDecimal> budgets,
                                                  Map<String, MoneyByCcy> investmentReconciliationRows,
                                                  String oficialRateAtPeriod) {
        BigDecimal totalArs = toArs(total, oficialRateAtPeriod);

        List<CashflowCategoryRow> categoryRows = new ArrayList<>();
        for (MergedCategoryTotal m : mergeByCategory(rows)) {
            BigDecimal amountArs = toArs(m.amount(), oficialRateAtPeriod);
            BigDecimal pctOfTotal = totalArs.compareTo(BigDecimal.ZERO) != 0
                    ? amountArs.divide(totalArs, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : BigDecimal.ZERO.setScale(2, RM);

            // Supuesto explícito: CategoryBudget no tiene campo de moneda → los presupuestos se tratan
            // siempre como ARS (limitación conocida).
            BigDecimal budgeted    = budgets.get(m.categoryId());
            BigDecimal pctOfBudget = null;
            if (budgeted != null && budgeted.compareTo(BigDecimal.ZERO) != 0) {
                pctOfBudget = amountArs.divide(budgeted, MC)
                        .multiply(BigDecimal.valueOf(100), MC)
                        .setScale(2, RM);
            }

            categoryRows.add(new CashflowCategoryRow(
                    m.categoryId() != null ? m.categoryId().toString() : null,
                    m.name(),
                    m.icon(),
                    m.color(),
                    m.amount().setScale(4, RM),
                    pctOfTotal,
                    budgeted != null ? new MoneyByCcy(budgeted.setScale(4, RM), BigDecimal.ZERO.setScale(4, RM)) : null,
                    pctOfBudget
            ));
        }

        // Filas sintéticas para los movimientos de inversión del período que no tienen categoría propia
        // (van vinculados al activo, no a una categoría de usuario): rescates y cobros
        // (capital/rendimiento/pérdida). Deben integrar el total de la sección ya que la cuenta ya los
        // registró como Transaction real.
        for (Map.Entry<String, MoneyByCcy> entry : investmentReconciliationRows.entrySet()) {
            MoneyByCcy amount = entry.getValue();
            // Se omite la fila si ninguno de los dos buckets aporta un monto positivo (idéntico
            // criterio al original: `amount.signum() <= 0` pero evaluado por moneda).
            if (amount == null || (amount.ars().signum() <= 0 && amount.usd().signum() <= 0)) continue;
            BigDecimal amountArs = toArs(amount, oficialRateAtPeriod);
            BigDecimal pctOfTotal = totalArs.compareTo(BigDecimal.ZERO) != 0
                    ? amountArs.divide(totalArs, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : BigDecimal.ZERO.setScale(2, RM);
            categoryRows.add(new CashflowCategoryRow(
                    null, entry.getKey(), INVESTMENT_ICON, INVESTMENT_COLOR,
                    amount.setScale(4, RM), pctOfTotal, null, null));
        }

        // Orden por monto (normalizado a ARS) descendente — reemplaza el ORDER BY que se quitó del SQL
        // (ya no tiene sentido a nivel SQL una vez que una fila puede combinar ARS + USD).
        categoryRows.sort(Comparator.comparing(
                (CashflowCategoryRow r) -> toArs(r.amount(), oficialRateAtPeriod)).reversed());

        BigDecimal totalBudgeted = budgets.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RM);

        return new CashflowFlowSection(
                total.setScale(4, RM),
                new MoneyByCcy(totalBudgeted, BigDecimal.ZERO.setScale(4, RM)),
                categoryRows);
    }

    /**
     * Suma el monto de las transacciones de inversión del período que coinciden con un
     * {@code investmentSourceType} y {@code TransactionType} dados (transacciones sin categoría propia:
     * RESCATE, COLLECTION_CAPITAL, COLLECTION_YIELD, COUPON_RENT, AMORTIZATION), bucketeado por la
     * moneda real de cada transacción ({@link Transaction#getCcy()}). Se distingue por tipo porque
     * COLLECTION_YIELD puede aparecer como INCOME (rendimiento positivo) o EXPENSE (pérdida de mercado
     * al cobrar).
     *
     * <p>Este es el punto exacto del bug original: antes sumaba {@code tx.getAmount()} de todas las
     * monedas en un único {@code BigDecimal}, así que un cobro de amortización de bono en USD (p. ej.
     * 10 USD) terminaba sumando "10" al total de ingresos sin distinguir que esos 10 eran dólares, no
     * pesos.
     */
    private MoneyByCcy sumInvestmentSourceTypeTotal(List<Transaction> investmentTxs, InvestmentSourceType sourceType, TransactionType type) {
        MoneyByCcy total = MoneyByCcy.zero();
        for (Transaction tx : investmentTxs) {
            if (tx.getInvestmentSourceType() != sourceType || tx.getType() != type) continue;
            total = total.plusIn(tx.getCcy(), tx.getAmount());
        }
        return total;
    }

    /**
     * Para meses proyectados: combina transacciones ya registradas, recurrentes activos
     * (para categorías sin registro aún) y presupuesto residual ("Otros").
     * Prioridad: registrado > recurrente template > presupuesto.
     */
    private CashflowFlowSection buildProjectedSection(UUID userId, TransactionType type, int year, int month, String oficialRateAtPeriod) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        CategoryType catType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;

        // ── 1. Transacciones ya registradas para este mes futuro ──────────────
        List<CategorySummaryProjection> registeredRows =
                transactionRepository.groupByCategory(userId, type, firstDay, lastDay);
        Set<UUID> registeredCatIds = registeredRows.stream()
                .map(CategorySummaryProjection::getCategoryId)
                .collect(Collectors.toSet());
        MoneyByCcy registeredTotal = sumProjections(registeredRows);
        List<MergedCategoryTotal> registeredMerged = mergeByCategory(registeredRows);

        // ── 2. Recurrentes para categorías no cubiertas por registradas ────────
        Map<UUID, MoneyByCcy> recurringByCategory = new LinkedHashMap<>();
        Map<UUID, RecurringMovement> catMeta = new LinkedHashMap<>();
        for (RecurringMovement rm : recurringMovementRepository
                .findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId)) {
            if (!type.name().equals(rm.getType())) continue;
            UUID catId = rm.getCategory() != null ? rm.getCategory().getId() : null;
            if (!registeredCatIds.contains(catId)) {
                MoneyByCcy amt = MoneyByCcy.zero().plusIn(rm.getCcy(), rm.getAmount().abs());
                recurringByCategory.merge(catId, amt, MoneyByCcy::add);
                catMeta.putIfAbsent(catId, rm);
            }
        }
        MoneyByCcy recurringOnlyTotal = recurringByCategory.values().stream()
                .reduce(MoneyByCcy.zero(), MoneyByCcy::add);

        // ── 3. Presupuesto ────────────────────────────────────────────────────
        // Supuesto explícito: CategoryBudget no tiene campo de moneda → siempre ARS (limitación conocida).
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
        MoneyByCcy budgetOnlyTotal = budgetOnlyMap.values().stream()
                .map(cb -> MoneyByCcy.zero().plusIn("ARS", cb.getAmount()))
                .reduce(MoneyByCcy.zero(), MoneyByCcy::add);

        // sectionTotal = suma de lo que se va a mostrar en filas (recurrente real + presupuestado individual).
        // No se usa max(budget, effective) porque los presupuestados ya están en effectiveTotal como filas propias.
        MoneyByCcy effectiveTotal = registeredTotal.add(recurringOnlyTotal).add(budgetOnlyTotal);
        MoneyByCcy sectionTotal = effectiveTotal;
        BigDecimal sectionTotalArs = toArs(sectionTotal, oficialRateAtPeriod);

        if (sectionTotalArs.compareTo(BigDecimal.ZERO) <= 0) {
            return new CashflowFlowSection(
                    MoneyByCcy.zero().setScale(4, RM),
                    new MoneyByCcy(budgetTotal.setScale(4, RM), BigDecimal.ZERO.setScale(4, RM)),
                    Collections.emptyList());
        }

        List<CashflowCategoryRow> rows = new ArrayList<>();

        // ── 4a. Filas de transacciones registradas ────────────────────────────
        for (MergedCategoryTotal m : registeredMerged) {
            BigDecimal amtArs = toArs(m.amount(), oficialRateAtPeriod);
            BigDecimal pctOfTotal = amtArs.divide(sectionTotalArs, MC)
                    .multiply(BigDecimal.valueOf(100), MC).setScale(2, RM);
            BigDecimal catBudget = m.categoryId() != null ? catBudgetMap.get(m.categoryId()) : null;
            BigDecimal pctOfBudget = (catBudget != null && catBudget.compareTo(BigDecimal.ZERO) != 0)
                    ? amtArs.divide(catBudget, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : null;
            rows.add(new CashflowCategoryRow(
                    m.categoryId() != null ? m.categoryId().toString() : null,
                    m.name(), m.icon(), m.color(),
                    m.amount().setScale(4, RM), pctOfTotal,
                    catBudget != null ? new MoneyByCcy(catBudget.setScale(4, RM), BigDecimal.ZERO.setScale(4, RM)) : null,
                    pctOfBudget
            ));
        }

        // ── 4b. Filas de recurrentes sin registrar (con comparación de presupuesto) ──
        for (Map.Entry<UUID, MoneyByCcy> entry : recurringByCategory.entrySet()) {
            UUID catId = entry.getKey();
            MoneyByCcy amt = entry.getValue().setScale(4, RM);
            RecurringMovement meta = catMeta.get(catId);
            BigDecimal amtArs = toArs(amt, oficialRateAtPeriod);
            BigDecimal pctOfTotal = amtArs.divide(sectionTotalArs, MC)
                    .multiply(BigDecimal.valueOf(100), MC).setScale(2, RM);
            BigDecimal catBudget = catId != null ? catBudgetMap.get(catId) : null;
            BigDecimal pctOfBudget = (catBudget != null && catBudget.compareTo(BigDecimal.ZERO) != 0)
                    ? amtArs.divide(catBudget, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RM)
                    : null;
            rows.add(new CashflowCategoryRow(
                    catId != null ? catId.toString() : null,
                    meta != null && meta.getCategory() != null ? meta.getCategory().getName() : "Sin categoría",
                    meta != null && meta.getCategory() != null ? meta.getCategory().getIcon() : "circle",
                    meta != null && meta.getCategory() != null ? meta.getCategory().getColor() : "#85948f",
                    amt, pctOfTotal,
                    catBudget != null ? new MoneyByCcy(catBudget.setScale(4, RM), BigDecimal.ZERO.setScale(4, RM)) : null,
                    pctOfBudget
            ));
        }

        // ── 4c. Filas solo presupuestadas (presupuesto como monto proyectado) ──
        for (Map.Entry<UUID, CategoryBudget> entry : budgetOnlyMap.entrySet()) {
            UUID catId = entry.getKey();
            CategoryBudget cb = entry.getValue();
            MoneyByCcy amt = new MoneyByCcy(cb.getAmount().setScale(4, RM), BigDecimal.ZERO.setScale(4, RM));
            BigDecimal amtArs = toArs(amt, oficialRateAtPeriod);
            BigDecimal pctOfTotal = amtArs.divide(sectionTotalArs, MC)
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

        MoneyByCcy totalBudgetedMoney = hasBudget
                ? new MoneyByCcy(budgetTotal.setScale(4, RM), BigDecimal.ZERO.setScale(4, RM))
                : MoneyByCcy.zero().setScale(4, RM);
        return new CashflowFlowSection(sectionTotal.setScale(4, RM), totalBudgetedMoney, rows);
    }

    /**
     * Ícono/color por defecto para todo instrumento de inversión en el cashflow: a diferencia de las
     * categorías de gasto/ingreso, un {@link InvestmentAsset} no tiene su propio ícono/color persistido.
     */
    private static final String INVESTMENT_ICON  = "trending-up";
    private static final String INVESTMENT_COLOR = "#6366f1";

    /**
     * Construye la sección de inversiones del cashflow a partir de las {@code Transaction} reales
     * vinculadas a activos de inversión (no de una categoría "inversiones" como antes). Se usa
     * {@code Transaction} y no {@code InvestmentMovement} porque el principal inicial de Plazo Fijo
     * y el cobro no dejan fila propia en movimientos — sólo transacción. Los revalúos nunca generan
     * transacción, así que quedan naturalmente excluidos por el filtro de {@code investmentSourceType}
     * de la query. Agrupa por activo sumando sólo SUSCRIPCION (bruto), bucketeado por la moneda real de
     * cada transacción: el RESCATE y los cobros (COLLECTION_CAPITAL/COLLECTION_YIELD) no se netean acá,
     * se reflejan aparte como filas de ingreso/egreso en {@code buildFlowSection} (ver
     * {@link #sumInvestmentSourceTypeTotal}), ya que la cuenta ya los registró como Transaction real.
     * Aprovecha para poblar {@code teaPct} desde la TNA del activo cuando está definida y es positiva.
     */
    private CashflowInvestmentSection buildInvestmentSection(List<Transaction> investmentTxs, MoneyByCcy preBalance, String oficialRateAtPeriod) {
        Map<UUID, InvestmentAsset> assetsById = new LinkedHashMap<>();
        Map<UUID, MoneyByCcy> totalByAssetId = new LinkedHashMap<>();
        MoneyByCcy orphanedTotal = MoneyByCcy.zero();
        for (Transaction tx : investmentTxs) {
            // Sólo SUSCRIPCION suma acá (bruto invertido). RESCATE, COLLECTION_CAPITAL y
            // COLLECTION_YIELD se ignoran: ya se contabilizan como ingreso/egreso en buildFlowSection,
            // y REVALUO nunca llega por la query.
            if (tx.getInvestmentSourceType() != InvestmentSourceType.SUSCRIPCION) {
                continue;
            }
            InvestmentAsset asset = tx.getInvestmentAsset();
            if (asset == null) {
                // Activo borrado (investmentAsset nuleado por ON DELETE SET NULL): se agrupa aparte
                // en vez de perderse, ver buildInvestmentSection javadoc.
                orphanedTotal = orphanedTotal.plusIn(tx.getCcy(), tx.getAmount());
                continue;
            }
            UUID assetId = asset.getId();
            assetsById.putIfAbsent(assetId, asset);
            totalByAssetId.merge(assetId, MoneyByCcy.zero().plusIn(tx.getCcy(), tx.getAmount()), MoneyByCcy::add);
        }

        List<CashflowInvestmentRow> instruments = new ArrayList<>(totalByAssetId.entrySet().stream()
                .map(entry -> {
                    InvestmentAsset asset = assetsById.get(entry.getKey());
                    MoneyByCcy amount = entry.getValue().setScale(4, RM);
                    BigDecimal teaPct = (asset.getTna() != null && asset.getTna().signum() > 0)
                            ? asset.getTna()
                            : null;
                    return new CashflowInvestmentRow(asset.getName(), INVESTMENT_ICON, INVESTMENT_COLOR, amount, teaPct);
                })
                .toList());

        if (!orphanedTotal.isZero()) {
            instruments.add(new CashflowInvestmentRow("Otras inversiones (activo eliminado)",
                    INVESTMENT_ICON, INVESTMENT_COLOR, orphanedTotal.setScale(4, RM), null));
        }

        MoneyByCcy total = instruments.stream()
                .map(CashflowInvestmentRow::amount)
                .reduce(MoneyByCcy.zero(), MoneyByCcy::add)
                .setScale(4, RM);

        BigDecimal pctOfPreBalance = null;
        BigDecimal preBalanceArs = toArs(preBalance, oficialRateAtPeriod);
        if (preBalanceArs.compareTo(BigDecimal.ZERO) != 0) {
            pctOfPreBalance = toArs(total, oficialRateAtPeriod).divide(preBalanceArs, MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(2, RM);
        }

        return new CashflowInvestmentSection(total, pctOfPreBalance, instruments);
    }

    private MoneyByCcy sumProjections(List<CategorySummaryProjection> rows) {
        MoneyByCcy total = MoneyByCcy.zero();
        for (CategorySummaryProjection row : rows) {
            if (row.getTotalAmount() == null) continue;
            total = total.plusIn(row.getCcy(), row.getTotalAmount());
        }
        return total;
    }

    private String buildPeriodLabel(LocalDate date) {
        String raw = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_AR).format(date);
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private String buildMonthShort(LocalDate date) {
        return DateTimeFormatter.ofPattern("MMM", LOCALE_AR).format(date);
    }
}
