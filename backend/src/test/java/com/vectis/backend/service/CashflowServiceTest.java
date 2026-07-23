package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.MonthPeriod;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CashflowResponse;
import com.vectis.backend.domain.entity.ExchangeRate;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.MonthPeriodRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.repository.TransactionRepository.CategorySummaryProjection;
import com.vectis.backend.repository.TransactionRepository.NetMovementProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashflowService")
class CashflowServiceTest {

    @InjectMocks
    private CashflowService cashflowService;

    @Mock private AccountRepository           accountRepository;
    @Mock private TransactionRepository       transactionRepository;
    @Mock private CategoryBudgetRepository    categoryBudgetRepository;
    @Mock private ExchangeRateRepository      exchangeRateRepository;
    @Mock private MonthPeriodService          monthPeriodService;
    @Mock private MonthPeriodRepository       monthPeriodRepository;
    @Mock private RecurringMovementRepository recurringMovementRepository;

    private User    user;
    private UUID    userId;
    private Account account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId).email("user@vectis.com")
                .fullName("Test User").passwordHash("hash")
                .build();

        account = Account.builder()
                .id(UUID.randomUUID()).user(user)
                .name("Cuenta Galicia").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("100000.0000"))
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── mes actual ("curso") ────────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow mes actual retorna isProjection=false y totales coherentes")
    void getCashflow_mesActual_retornaDatosReales() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(List.of(netProj(account.getId(), new BigDecimal("10000.0000"))));

        CategorySummaryProjection incomeRow  = proj(UUID.randomUUID(), "Sueldo",       "briefcase", "#22c55e", new BigDecimal("50000.00"));
        CategorySummaryProjection expenseRow = proj(UUID.randomUUID(), "Alimentación", "cart",      "#ef4444", new BigDecimal("20000.00"));

        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay)).willReturn(List.of(incomeRow));
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay)).willReturn(List.of(expenseRow));
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isProjection()).isFalse();
        assertThat(result.getStatus()).isEqualTo("curso");
        assertThat(result.getIncome().total()).isEqualByComparingTo("50000.0000");
        assertThat(result.getExpenses().total()).isEqualByComparingTo("20000.0000");
        assertThat(result.getPreInvestmentBalance().operativeResult()).isEqualByComparingTo("30000.0000");
        assertThat(result.getOpeningBalance().accounts()).hasSize(1);
        assertThat(result.getClosingBalance().accounts()).hasSize(1);
        assertThat(result.getOpeningBalance().accounts().get(0).balance()).isEqualByComparingTo("110000.0000");
    }

    // ─── mes futuro (proyección) ──────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow mes futuro retorna isProjection=true")
    void getCashflow_mesFuturo_retornaProyeccion() {
        LocalDate futureMonth = LocalDate.now().plusMonths(1);
        int year  = futureMonth.getYear();
        int month = futureMonth.getMonthValue();
        LocalDate firstDay = futureMonth.withDayOfMonth(1);

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("proyectado");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // projected section stubs
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isProjection()).isTrue();
        assertThat(result.getStatus()).isEqualTo("proyectado");
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonth()).isEqualTo(month);
    }

    // ─── cuenta excluida no aparece en balances ───────────────────────────────

    @Test
    @DisplayName("getCashflow cuenta con includeInCashflow=false no aparece en opening/closing")
    void getCashflow_cuentaExcluidaNoAparece() {
        LocalDate today    = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(today.getYear()), eq(today.getMonthValue()), any(LocalDate.class)))
                .willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, today.getYear(), today.getMonthValue()))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay)).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, today.getYear(), today.getMonthValue());

        assertThat(result.getOpeningBalance().accounts()).isEmpty();
        assertThat(result.getOpeningBalance().total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getClosingBalance().accounts()).isEmpty();
    }

    // ─── período cerrado (mes pasado) ─────────────────────────────────────────

    @Test
    @DisplayName("getCashflow mes cerrado retorna status=cerrado")
    void getCashflow_mesCerrado_retornaStatusCerrado() {
        LocalDate pastMonth = LocalDate.now().minusMonths(1);
        int year  = pastMonth.getYear();
        int month = pastMonth.getMonthValue();
        LocalDate firstDay = pastMonth.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("cerrado");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay)).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getStatus()).isEqualTo("cerrado");
        assertThat(result.isProjection()).isFalse();
    }

    // ─── income refleja presupuesto cuando existe CategoryBudget ─────────────

    @Test
    @DisplayName("getCashflow refleja budgeted y pctOfBudget en income.byCategory cuando existe presupuesto")
    void getCashflow_income_conPresupuesto_refleja_budgetedYPct() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        UUID incomeCatId    = UUID.randomUUID();
        BigDecimal amount   = new BigDecimal("50000.00");
        BigDecimal budgeted = new BigDecimal("60000.00");

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay))
                .willReturn(List.of(proj(incomeCatId, "Sueldo", "briefcase", "#22c55e", amount)));
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay))
                .willReturn(Collections.emptyList());

        Category mockCat = mock(Category.class);
        given(mockCat.getId()).willReturn(incomeCatId);
        given(mockCat.getType()).willReturn(CategoryType.INCOME);
        CategoryBudget mockBudget = mock(CategoryBudget.class);
        given(mockBudget.getCategory()).willReturn(mockCat);
        given(mockBudget.getAmount()).willReturn(budgeted);

        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay))
                .willReturn(List.of(mockBudget));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getIncome().byCategory()).hasSize(1);
        assertThat(result.getIncome().byCategory().get(0).budgeted()).isNotNull();
        assertThat(result.getIncome().byCategory().get(0).pctOfBudget())
                .isNotNull()
                .isEqualByComparingTo("83.33");
        assertThat(result.getIncome().totalBudgeted()).isEqualByComparingTo("60000.0000");
        assertThat(result.getExpenses().totalBudgeted()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── savingRate se calcula correctamente ──────────────────────────────────

    @Test
    @DisplayName("getCashflow calcula savingRatePct correctamente — ingresos 100k, egresos 25k → 75%")
    void getCashflow_savingRatePct_esCorrecta() {
        LocalDate today    = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(today.getYear()), eq(today.getMonthValue()), any(LocalDate.class)))
                .willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, today.getYear(), today.getMonthValue()))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Sueldo", "briefcase", "#22c55e", new BigDecimal("100000.00"))));
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Gastos", "cart",      "#ef4444", new BigDecimal("25000.00"))));
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, today.getYear(), today.getMonthValue());

        assertThat(result.getPreInvestmentBalance().savingRatePct()).isEqualByComparingTo("75.00");
    }

    // ─── buildProjectedSection — nuevos tests ─────────────────────────────────

    @Test
    @DisplayName("buildProjectedSection: categoría con recurrente muestra comparación de presupuesto; categoría solo presupuestada aparece como fila individual")
    void buildProjectedSection_conPresupuesto_mostradoIndividualmenteYConComparacion() {
        LocalDate future    = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("proyectado");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // Categoría A — tiene recurrente ($5k) Y presupuesto ($10k)
        UUID catAId = UUID.randomUUID();
        Category catA = mock(Category.class);
        given(catA.getId()).willReturn(catAId);
        given(catA.getName()).willReturn("Servicios");
        given(catA.getIcon()).willReturn("bolt");
        given(catA.getColor()).willReturn("#f59e0b");
        given(catA.getType()).willReturn(CategoryType.EXPENSE);

        RecurringMovement rm = RecurringMovement.builder()
                .id(UUID.randomUUID()).user(user)
                .description("Internet").amount(new BigDecimal("5000.00"))
                .ccy("ARS").type("EXPENSE").category(catA).dayOfMonth(5).active(true)
                .createdAt(OffsetDateTime.now())
                .build();
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(List.of(rm));

        CategoryBudget budgetA = mock(CategoryBudget.class);
        given(budgetA.getAmount()).willReturn(new BigDecimal("10000.00"));
        given(budgetA.getCategory()).willReturn(catA);

        // Categoría B — solo presupuesto ($8k), sin recurrente
        UUID catBId = UUID.randomUUID();
        Category catB = mock(Category.class);
        given(catB.getId()).willReturn(catBId);
        given(catB.getName()).willReturn("Entretenimiento");
        given(catB.getIcon()).willReturn("music");
        given(catB.getColor()).willReturn("#8b5cf6");
        given(catB.getType()).willReturn(CategoryType.EXPENSE);

        CategoryBudget budgetB = mock(CategoryBudget.class);
        given(budgetB.getAmount()).willReturn(new BigDecimal("8000.00"));
        given(budgetB.getCategory()).willReturn(catB);

        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(List.of(budgetA, budgetB));
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class),
                any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        // sectionTotal = effectiveTotal = 5000 (recurrente) + 8000 (presupuestado B) = 13000
        assertThat(result.getExpenses().total()).isEqualByComparingTo("13000.0000");
        // totalBudgeted = 10000 + 8000 = 18000
        assertThat(result.getExpenses().totalBudgeted()).isEqualByComparingTo("18000.0000");

        // 2 filas: Servicios (recurrente) + Entretenimiento (solo presupuesto)
        assertThat(result.getExpenses().byCategory()).hasSize(2);
        assertThat(result.getExpenses().byCategory().stream()
                .anyMatch(r -> "Otros gastos".equals(r.name()))).isFalse();

        // Categoría A (recurrente): muestra pctOfBudget = 5000/10000 = 50%
        var serviciosRow = result.getExpenses().byCategory().stream()
                .filter(r -> "Servicios".equals(r.name())).findFirst().orElseThrow();
        assertThat(serviciosRow.budgeted()).isEqualByComparingTo("10000.0000");
        assertThat(serviciosRow.pctOfBudget()).isEqualByComparingTo("50.00");

        // Categoría B (solo presupuesto): monto = presupuesto, pctOfBudget = 100%
        var entretenimientoRow = result.getExpenses().byCategory().stream()
                .filter(r -> "Entretenimiento".equals(r.name())).findFirst().orElseThrow();
        assertThat(entretenimientoRow.amount()).isEqualByComparingTo("8000.0000");
        assertThat(entretenimientoRow.pctOfBudget()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("buildProjectedSection sin presupuesto solo muestra recurrentes y totalBudgeted=0")
    void buildProjectedSection_sinPresupuesto_soloRecurrentes_totalBudgetedCero() {
        LocalDate future = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("proyectado");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        UUID catId = UUID.randomUUID();
        Category cat = mock(Category.class);
        given(cat.getId()).willReturn(catId);
        given(cat.getName()).willReturn("Sueldo");
        given(cat.getIcon()).willReturn("briefcase");
        given(cat.getColor()).willReturn("#22c55e");
        given(cat.getType()).willReturn(CategoryType.INCOME);

        RecurringMovement rm = RecurringMovement.builder()
                .id(UUID.randomUUID()).user(user)
                .description("Sueldo").amount(new BigDecimal("100000.00"))
                .ccy("ARS").type("INCOME").category(cat).dayOfMonth(25).active(true)
                .createdAt(OffsetDateTime.now())
                .build();

        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(List.of(rm));

        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class),
                any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        // total = recurringTotal = 100000
        assertThat(result.getIncome().total()).isEqualByComparingTo("100000.0000");
        // no budget rows → totalBudgeted = 0
        assertThat(result.getIncome().totalBudgeted()).isEqualByComparingTo(BigDecimal.ZERO);
        // 1 row
        assertThat(result.getIncome().byCategory()).hasSize(1);
        assertThat(result.getIncome().byCategory().get(0).name()).isEqualTo("Sueldo");
    }

    // ─── hasLiquidityDeficit ──────────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow balance pre-inversión negativo → hasLiquidityDeficit=true")
    void getCashflow_balancePreInversionNegativo_seteaHasLiquidityDeficit() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        // Cuenta con saldo 0
        Account zeroAccount = Account.builder()
                .id(UUID.randomUUID()).user(user)
                .name("Cuenta cero").kind("Banco").ccy("ARS")
                .balance(BigDecimal.ZERO)
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(zeroAccount));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // income=10k, expense=50k → preBalance = 0 + (10k - 50k) = -40k
        given(transactionRepository.groupByCategory(userId, TransactionType.INCOME,  firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Sueldo", "briefcase", "#22c55e", new BigDecimal("10000.00"))));
        given(transactionRepository.groupByCategory(userId, TransactionType.EXPENSE, firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Gastos", "cart",      "#ef4444", new BigDecimal("50000.00"))));
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isHasLiquidityDeficit()).isTrue();
        assertThat(new java.math.BigDecimal(result.getLiquidityDeficit()))
                .isEqualByComparingTo("40000.0000");
    }

    // ─── needsConfirmation ────────────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow mes futuro sin MonthPeriod PROJECTED → needsConfirmation=true")
    void getCashflow_mesProyectadoSinRegistro_seteaNeedsConfirmation() {
        LocalDate future = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("proyectado");
        // No MonthPeriod record at all
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isNeedsConfirmation()).isTrue();
    }

    @Test
    @DisplayName("getCashflow mes futuro con MonthPeriod PROJECTED → needsConfirmation=false")
    void getCashflow_mesProyectadoConRegistroProjected_noNeedsConfirmation() {
        LocalDate future = LocalDate.now().plusMonths(1);
        int year  = future.getYear();
        int month = future.getMonthValue();

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class)))
                .willReturn("proyectado");
        MonthPeriod projectedMp = MonthPeriod.builder()
                .id(UUID.randomUUID()).user(user)
                .year(year).month(month)
                .status("PROJECTED")
                .openedAt(java.time.OffsetDateTime.now())
                .build();
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month))
                .willReturn(Optional.of(projectedMp));

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isNeedsConfirmation()).isFalse();
    }

    // ─── acumulación de saldos proyectados consecutivos ──────────────────────

    @Test
    @DisplayName("dos meses proyectados consecutivos: openingBalance mes N+2 hereda closingBalance de N+1")
    void getCashflow_dosProyectadosConsecutivos_acumulanSaldo() {
        LocalDate today  = LocalDate.now();
        LocalDate n1     = today.plusMonths(1).withDayOfMonth(1);
        LocalDate n2     = today.plusMonths(2).withDayOfMonth(1);
        int n1year = n1.getYear();  int n1month = n1.getMonthValue();
        int n2year = n2.getYear();  int n2month = n2.getMonthValue();
        int nyear  = today.getYear(); int nmonth = today.getMonthValue();

        // N = "curso", N+1 y N+2 = "proyectado"
        given(monthPeriodService.getStatus(eq(user), eq(n2year), eq(n2month), any(LocalDate.class))).willReturn("proyectado");
        given(monthPeriodService.getStatus(eq(user), eq(n1year), eq(n1month), any(LocalDate.class))).willReturn("proyectado");
        given(monthPeriodService.getStatus(eq(user), eq(nyear),  eq(nmonth),  any(LocalDate.class))).willReturn("curso");

        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, n1year, n1month)).willReturn(Optional.empty());
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, n2year, n2month)).willReturn(Optional.empty());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(List.of(account));
        // account.balance = 100000, sin movimientos netos → opening N+1 = 100000
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // Recurrentes: income 50000, expense 30000 → closing N+1 = 100000 + 50000 - 30000 = 120000
        UUID incCatId = UUID.randomUUID();
        Category incCat = mock(Category.class);
        given(incCat.getId()).willReturn(incCatId);
        given(incCat.getName()).willReturn("Sueldo");
        given(incCat.getIcon()).willReturn("briefcase");
        given(incCat.getColor()).willReturn("#22c55e");
        given(incCat.getType()).willReturn(CategoryType.INCOME);

        UUID expCatId = UUID.randomUUID();
        Category expCat = mock(Category.class);
        given(expCat.getId()).willReturn(expCatId);
        given(expCat.getName()).willReturn("Gastos");
        given(expCat.getIcon()).willReturn("cart");
        given(expCat.getColor()).willReturn("#ef4444");
        given(expCat.getType()).willReturn(CategoryType.EXPENSE);

        RecurringMovement rmIncome = RecurringMovement.builder()
                .id(UUID.randomUUID()).user(user)
                .description("Sueldo").amount(new BigDecimal("50000.00"))
                .ccy("ARS").type("INCOME").category(incCat).dayOfMonth(25).active(true)
                .createdAt(OffsetDateTime.now()).build();
        RecurringMovement rmExpense = RecurringMovement.builder()
                .id(UUID.randomUUID()).user(user)
                .description("Gastos").amount(new BigDecimal("30000.00"))
                .ccy("ARS").type("EXPENSE").category(expCat).dayOfMonth(5).active(true)
                .createdAt(OffsetDateTime.now()).build();
        given(recurringMovementRepository.findAllByUser_IdAndActiveTrueAndDeletedAtIsNull(userId))
                .willReturn(List.of(rmIncome, rmExpense));
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(eq(userId), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, n2year, n2month);

        assertThat(result.isProjection()).isTrue();
        // N+2 debe abrir con el cierre proyectado de N+1 = 100000 + 50000 - 30000 = 120000
        assertThat(result.getOpeningBalance().total()).isEqualByComparingTo("120000.0000");
    }

    // ─── oficialRateAtPeriod ──────────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow incluye oficialRateAtPeriod cuando existe cotizacion para el ultimo dia del mes")
    void getCashflow_includesOficialRateAtPeriod_whenExactDateFound() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(), any(LocalDate.class), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        ExchangeRate rate = ExchangeRate.builder()
                .id(UUID.randomUUID()).rateType("OFICIAL")
                .buy(new BigDecimal("1060.0000")).sell(new BigDecimal("1062.5000"))
                .rateDate(lastDay).source("dolarapi.com").createdAt(OffsetDateTime.now()).build();
        given(exchangeRateRepository.findByRateTypeAndRateDate("OFICIAL", lastDay)).willReturn(Optional.of(rate));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getOficialRateAtPeriod()).isEqualTo("1062.5000");
    }

    @Test
    @DisplayName("getCashflow usa la cotizacion mas reciente cuando no hay una para el ultimo dia del mes")
    void getCashflow_fallsBackToMostRecentRate_whenExactDateMissing() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(), any(LocalDate.class), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        ExchangeRate fallback = ExchangeRate.builder()
                .id(UUID.randomUUID()).rateType("OFICIAL")
                .buy(new BigDecimal("1058.0000")).sell(new BigDecimal("1060.0000"))
                .rateDate(lastDay.minusDays(3)).source("dolarapi.com").createdAt(OffsetDateTime.now()).build();
        given(exchangeRateRepository.findByRateTypeAndRateDate("OFICIAL", lastDay)).willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("OFICIAL")).willReturn(Optional.of(fallback));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getOficialRateAtPeriod()).isEqualTo("1060.0000");
    }

    @Test
    @DisplayName("getCashflow deja oficialRateAtPeriod en null cuando no hay cotizacion disponible")
    void getCashflow_nullRateAtPeriod_whenNoRateAvailable() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(), any(LocalDate.class), any(LocalDate.class))).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        given(exchangeRateRepository.findByRateTypeAndRateDate(anyString(), any(LocalDate.class))).willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc(anyString())).willReturn(Optional.empty());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getOficialRateAtPeriod()).isNull();
    }

    // ─── buildInvestmentSection (AF411954 — Vincular Inversiones con Cuentas) ─

    @Test
    @DisplayName("investments: lee de Transaction real vinculada al activo (no de una categoría 'inversiones')")
    void investments_readsFromRealInvestmentTransactions() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("LECAP S31G5")
                .type(InvestmentAssetType.LETRA).currency("ARS")
                .principal(new BigDecimal("500000.0000"))
                .tna(BigDecimal.ZERO)
                .build();
        Transaction suscripcion = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("500000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(suscripcion));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments()).hasSize(1);
        assertThat(result.getInvestments().instruments().get(0).name()).isEqualTo("LECAP S31G5");
        assertThat(result.getInvestments().instruments().get(0).amount()).isEqualByComparingTo("500000.0000");
        assertThat(result.getInvestments().total()).isEqualByComparingTo("500000.0000");
    }

    @Test
    @DisplayName("investments: una SUSCRIPCION con activo borrado (investmentAsset=null) no explota y se agrupa como 'Otras inversiones (activo eliminado)'")
    void investments_orphanedSuscripcionAppearsAsSyntheticRow() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        Transaction orphanedSuscripcion = Transaction.builder()
                .investmentAsset(null).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(orphanedSuscripcion));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments()).hasSize(1);
        assertThat(result.getInvestments().instruments().get(0).name()).isEqualTo("Otras inversiones (activo eliminado)");
        assertThat(result.getInvestments().instruments().get(0).amount()).isEqualByComparingTo("1000000.0000");
        assertThat(result.getInvestments().instruments().get(0).teaPct()).isNull();
        assertThat(result.getInvestments().total()).isEqualByComparingTo("1000000.0000");
    }

    @Test
    @DisplayName("investments: mezcla de activo vivo + huérfano suma ambos al total sin perder ninguno")
    void investments_liveAndOrphanedSuscripcionesBothCountTowardTotal() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("LECAP S31G5")
                .type(InvestmentAssetType.LETRA).currency("ARS")
                .principal(new BigDecimal("500000.0000"))
                .tna(BigDecimal.ZERO)
                .build();
        Transaction liveSuscripcion = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("500000.0000")).build();
        Transaction orphanedSuscripcion = Transaction.builder()
                .investmentAsset(null).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(liveSuscripcion, orphanedSuscripcion));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments()).hasSize(2);
        assertThat(result.getInvestments().total()).isEqualByComparingTo("1500000.0000");
    }

    @Test
    @DisplayName("investments: suma sólo SUSCRIPCION (bruto); el RESCATE del mismo activo no se netea acá")
    void investments_onlySumsSuscripcionGross() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).name("FCI Ahorro")
                .type(InvestmentAssetType.FCI).currency("ARS")
                .principal(new BigDecimal("400000.0000"))
                .tna(BigDecimal.ZERO)
                .build();
        Transaction suscripcion = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("500000.0000")).build();
        Transaction rescate = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.RESCATE)
                .amount(new BigDecimal("100000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(suscripcion, rescate));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        // "Destinado a inversiones" queda en el bruto suscripto (500.000): el rescate no resta acá,
        // se refleja aparte como ingreso (ver investments_rescateAppearsAsIncome).
        assertThat(result.getInvestments().instruments()).hasSize(1);
        assertThat(result.getInvestments().instruments().get(0).amount()).isEqualByComparingTo("500000.0000");
    }

    @Test
    @DisplayName("investments: el RESCATE aparece como ingreso en 'Ingresos contabilizados', no netea la sección de inversiones")
    void investments_rescateAppearsAsIncome() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("FCI Ahorro")
                .type(InvestmentAssetType.FCI).currency("ARS")
                .principal(new BigDecimal("300000.0000"))
                .tna(BigDecimal.ZERO)
                .build();
        Transaction rescate = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.RESCATE).type(TransactionType.INCOME)
                .amount(new BigDecimal("80000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(rescate));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getIncome().total()).isEqualByComparingTo("80000.0000");
        assertThat(result.getIncome().byCategory()).hasSize(1);
        assertThat(result.getIncome().byCategory().get(0).name()).isEqualTo("Rescates de inversión");
        assertThat(result.getIncome().byCategory().get(0).amount()).isEqualByComparingTo("80000.0000");
        assertThat(result.getIncome().byCategory().get(0).categoryId()).isNull();
        // Ningún SUSCRIPCION en este caso: la sección de inversiones queda vacía.
        assertThat(result.getInvestments().instruments()).isEmpty();
    }

    @Test
    @DisplayName("investments: el cobro de una inversión (COLLECTION_CAPITAL/COLLECTION_YIELD) impacta el resultado operativo del mes, no queda en cero")
    void investments_collectionImpactsOperativeResult() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("LECAP S31G5")
                .type(InvestmentAssetType.LETRA).currency("ARS")
                .principal(BigDecimal.ZERO).tna(BigDecimal.ZERO)
                .build();
        Transaction collectionCapital = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.COLLECTION_CAPITAL).type(TransactionType.INCOME)
                .amount(new BigDecimal("1000000.0000")).build();
        Transaction collectionYield = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.COLLECTION_YIELD).type(TransactionType.INCOME)
                .amount(new BigDecimal("150000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(collectionCapital, collectionYield));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        // Ni el capital ni el rendimiento del cobro tienen categoría propia, pero deben sumar igual
        // al total de "Ingresos" y por lo tanto al resultado operativo del mes (no debe quedar en cero
        // pese a que la cuenta efectivamente recibió $1.150.000).
        assertThat(result.getIncome().total()).isEqualByComparingTo("1150000.0000");
        assertThat(result.getIncome().byCategory())
                .extracting("name")
                .containsExactlyInAnyOrder("Cobro de inversión (capital)", "Cobro de inversión (rendimiento)");
        assertThat(result.getPreInvestmentBalance().operativeResult()).isEqualByComparingTo("1150000.0000");
        // El cobro tampoco duplica su marca en la sección de "Inversiones" (esa sección sólo agrupa
        // el bruto SUSCRIPTO histórico, no el cobro).
        assertThat(result.getInvestments().instruments()).isEmpty();
    }

    @Test
    @DisplayName("investments: confirmar cupones de renta/amortización agrega dos filas nuevas de ingreso y suma al resultado operativo")
    void investments_couponRentAndAmortizationAppearAsNewIncomeRows() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("AL30")
                .type(InvestmentAssetType.BONO).currency("USD")
                .principal(BigDecimal.ZERO).tna(BigDecimal.ZERO)
                .build();
        Transaction couponRent = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.COUPON_RENT).type(TransactionType.INCOME)
                .amount(new BigDecimal("2700.0000")).build();
        Transaction amortization = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.AMORTIZATION).type(TransactionType.INCOME)
                .amount(new BigDecimal("80000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(couponRent, amortization));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getIncome().total()).isEqualByComparingTo("82700.0000");
        assertThat(result.getIncome().byCategory())
                .extracting("name")
                .containsExactlyInAnyOrder("Renta de inversión (cupones)", "Amortización de inversión");
        assertThat(result.getPreInvestmentBalance().operativeResult()).isEqualByComparingTo("82700.0000");
    }

    @Test
    @DisplayName("investments: ignora transacciones con investmentSourceType distinto de SUSCRIPCION/RESCATE")
    void investments_ignoresNonSuscripcionRescateSourceType() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        // COLLECTION_CAPITAL sí puede llegar por la query real (findInvestmentTransactionsForCashflow
        // las incluye a propósito, ver su Javadoc) — buildInvestmentSection debe ignorarlas igual,
        // ya que se contabilizan como ingreso en buildFlowSection, no en la sección "Inversiones".
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("Cobro viejo")
                .type(InvestmentAssetType.LETRA).currency("ARS")
                .principal(BigDecimal.ZERO).tna(BigDecimal.ZERO)
                .build();
        Transaction collection = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.COLLECTION_CAPITAL)
                .amount(new BigDecimal("999999.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(collection));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments()).isEmpty();
        assertThat(result.getInvestments().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("investments: sin transacciones vinculadas (activo excluido del cashflow o sin cuenta) la sección queda vacía")
    void investments_emptyWhenNoLinkedTransactions() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());
        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments()).isEmpty();
        assertThat(result.getInvestments().total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getInvestments().pctOfPreBalance()).isNull();
    }

    @Test
    @DisplayName("investments: puebla teaPct desde la TNA del activo cuando es positiva")
    void investments_populatesTeaPctFromAssetTna() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());

        InvestmentAsset asset = InvestmentAsset.builder()
                .id(UUID.randomUUID()).name("Plazo Fijo 60d")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("300000.0000"))
                .tna(new BigDecimal("60.0000"))
                .build();
        Transaction suscripcion = Transaction.builder()
                .investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .amount(new BigDecimal("300000.0000")).build();

        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(List.of(suscripcion));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.getInvestments().instruments().get(0).teaPct()).isEqualByComparingTo("60.0000");
    }

    @Test
    @DisplayName("closingBalance refleja una transacción de suscripción de inversión sin cambios de fórmula")
    void closingBalance_reflectsInvestmentSubscriptionTransaction() {
        LocalDate today    = LocalDate.now();
        int year  = today.getYear();
        int month = today.getMonthValue();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(monthPeriodService.getStatus(eq(user), eq(year), eq(month), any(LocalDate.class))).willReturn("curso");
        given(monthPeriodRepository.findByUser_IdAndYearAndMonth(userId, year, month)).willReturn(Optional.empty());
        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(List.of(account));
        given(transactionRepository.groupByCategory(eq(userId), any(TransactionType.class), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findLatestPerCategoryOnOrBefore(userId, firstDay)).willReturn(Collections.emptyList());
        given(transactionRepository.findInvestmentTransactionsForCashflow(userId, firstDay, lastDay))
                .willReturn(Collections.emptyList());

        // La suscripción de $30.000 ya impactó la cuenta como una Transaction EXPENSE real:
        // netMovementsForAccounts (el mecanismo YA existente, sin cambios) refleja ese egreso.
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(List.of(netProj(account.getId(), new BigDecimal("-30000.0000"))));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        // account.balance (100000) + neto (-30000) = 70000, sin tocar ninguna fórmula de CashflowService.
        assertThat(result.getClosingBalance().accounts().get(0).balance()).isEqualByComparingTo("70000.0000");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private CategorySummaryProjection proj(UUID catId, String name, String icon, String color, BigDecimal amount) {
        return new CategorySummaryProjection() {
            @Override public UUID getCategoryId()        { return catId;  }
            @Override public String getCategoryName()    { return name;   }
            @Override public String getCategoryIcon()    { return icon;   }
            @Override public String getCategoryColor()   { return color;  }
            @Override public BigDecimal getTotalAmount() { return amount; }
        };
    }

    private NetMovementProjection netProj(UUID accountId, BigDecimal netAmount) {
        return new NetMovementProjection() {
            @Override public UUID getAccountId()         { return accountId; }
            @Override public BigDecimal getNetAmount()   { return netAmount; }
        };
    }
}
