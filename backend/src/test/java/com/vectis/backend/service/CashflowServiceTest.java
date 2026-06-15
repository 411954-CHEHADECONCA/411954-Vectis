package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CashflowResponse;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryBudgetRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CashflowService")
class CashflowServiceTest {

    @InjectMocks
    private CashflowService cashflowService;

    @Mock private AccountRepository       accountRepository;
    @Mock private TransactionRepository   transactionRepository;
    @Mock private CategoryBudgetRepository categoryBudgetRepository;

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
                .remunerada(false).includeInCashflow(true)
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

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(List.of(netProj(account.getId(), new BigDecimal("10000.0000"))));

        CategorySummaryProjection incomeRow  = proj(UUID.randomUUID(), "Sueldo",       "briefcase", "#22c55e", new BigDecimal("50000.00"));
        CategorySummaryProjection expenseRow = proj(UUID.randomUUID(), "Alimentación", "cart",      "#ef4444", new BigDecimal("20000.00"));

        given(transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay)).willReturn(List.of(incomeRow));
        given(transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay)).willReturn(List.of(expenseRow));
        given(categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isProjection()).isFalse();
        assertThat(result.status()).isEqualTo("curso");
        assertThat(result.income().total()).isEqualByComparingTo("50000.0000");
        assertThat(result.expenses().total()).isEqualByComparingTo("20000.0000");
        assertThat(result.preInvestmentBalance().operativeResult()).isEqualByComparingTo("30000.0000");
        assertThat(result.openingBalance().accounts()).hasSize(1);
        assertThat(result.closingBalance().accounts()).hasSize(1);
        // saldo calculado = balance + net = 100000 + 10000 = 110000
        assertThat(result.openingBalance().accounts().get(0).balance()).isEqualByComparingTo("110000.0000");
    }

    // ─── mes futuro (proyección) ──────────────────────────────────────────────

    @Test
    @DisplayName("getCashflow mes futuro retorna isProjection=true")
    void getCashflow_mesFuturo_retornaProyeccion() {
        LocalDate futureMonth = LocalDate.now().plusMonths(1);
        int year  = futureMonth.getYear();
        int month = futureMonth.getMonthValue();

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), eq("INCOME"),  any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(eq(userId), eq("EXPENSE"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.isProjection()).isTrue();
        assertThat(result.status()).isEqualTo("proyectado");
        assertThat(result.year()).isEqualTo(year);
        assertThat(result.month()).isEqualTo(month);
    }

    // ─── cuenta excluida no aparece en balances ───────────────────────────────

    @Test
    @DisplayName("getCashflow cuenta con includeInCashflow=false no aparece en opening/closing")
    void getCashflow_cuentaExcluidaNoAparece() {
        LocalDate today    = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay)).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, today.getYear(), today.getMonthValue());

        assertThat(result.openingBalance().accounts()).isEmpty();
        assertThat(result.openingBalance().total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.closingBalance().accounts()).isEmpty();
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

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay)).willReturn(Collections.emptyList());
        given(categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay)).willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.status()).isEqualTo("cerrado");
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

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId)).willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay))
                .willReturn(List.of(proj(incomeCatId, "Sueldo", "briefcase", "#22c55e", amount)));
        given(transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay))
                .willReturn(Collections.emptyList());

        Category mockCat = mock(Category.class);
        given(mockCat.getId()).willReturn(incomeCatId);
        given(mockCat.getType()).willReturn(CategoryType.INCOME);
        CategoryBudget mockBudget = mock(CategoryBudget.class);
        given(mockBudget.getCategory()).willReturn(mockCat);
        given(mockBudget.getAmount()).willReturn(budgeted);

        given(categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay))
                .willReturn(List.of(mockBudget));

        CashflowResponse result = cashflowService.getCashflow(user, year, month);

        assertThat(result.income().byCategory()).hasSize(1);
        // budgeted debe estar presente en la fila
        assertThat(result.income().byCategory().get(0).budgeted()).isNotNull();
        // pctOfBudget = 50000 / 60000 × 100 ≈ 83.33
        assertThat(result.income().byCategory().get(0).pctOfBudget())
                .isNotNull()
                .isEqualByComparingTo("83.33");
        // totalBudgeted incluye el presupuesto aunque no haya más categorías con transacciones
        assertThat(result.income().totalBudgeted()).isEqualByComparingTo("60000.0000");
        // egresos sin presupuesto → totalBudgeted = 0
        assertThat(result.expenses().totalBudgeted()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── savingRate se calcula correctamente ──────────────────────────────────

    @Test
    @DisplayName("getCashflow calcula savingRatePct correctamente — ingresos 100k, egresos 25k → 75%")
    void getCashflow_savingRatePct_esCorrecta() {
        LocalDate today    = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        given(accountRepository.findAllByUser_IdAndIncludeInCashflowTrue(userId))
                .willReturn(List.of(account));
        given(transactionRepository.netMovementsForAccounts(eq(userId), anyList(), any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        given(transactionRepository.groupByCategory(userId, "INCOME",  firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Sueldo", "briefcase", "#22c55e", new BigDecimal("100000.00"))));
        given(transactionRepository.groupByCategory(userId, "EXPENSE", firstDay, lastDay))
                .willReturn(List.of(proj(UUID.randomUUID(), "Gastos", "cart",      "#ef4444", new BigDecimal("25000.00"))));
        given(categoryBudgetRepository.findAllByUser_IdAndValidFromEager(userId, firstDay))
                .willReturn(Collections.emptyList());

        CashflowResponse result = cashflowService.getCashflow(user, today.getYear(), today.getMonthValue());

        assertThat(result.preInvestmentBalance().savingRatePct()).isEqualByComparingTo("75.00");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private CategorySummaryProjection proj(UUID catId, String name, String icon, String color, BigDecimal amount) {
        return new CategorySummaryProjection() {
            @Override public UUID getCategoryId()      { return catId;  }
            @Override public String getCategoryName()  { return name;   }
            @Override public String getCategoryIcon()  { return icon;   }
            @Override public String getCategoryColor() { return color;  }
            @Override public BigDecimal getTotalAmount() { return amount; }
        };
    }

    private NetMovementProjection netProj(UUID accountId, BigDecimal netAmount) {
        return new NetMovementProjection() {
            @Override public UUID getAccountId()      { return accountId; }
            @Override public BigDecimal getNetAmount() { return netAmount; }
        };
    }
}
