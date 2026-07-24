package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión directa del bug de transacciones de inversión huérfanas invisibles en el cashflow:
 * un activo borrado deja {@code investmentAsset = NULL} (ON DELETE SET NULL, migración V042), y
 * este repositorio necesita seguir recuperando esas filas por {@code investmentSourceType} — pero
 * solo si nunca tuvieron categoría asignada, para no duplicarlas con {@link TransactionRepository#groupByCategory}.
 * Ningún test con Mockito puede detectar un WHERE roto (el mock devuelve lo que se le indique);
 * hace falta ejecutar el JPQL real, de ahí este primer {@code @DataJpaTest} del proyecto.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TransactionRepository transactionRepository;

    private User user;
    private Category expenseCategory;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User")
                .build());

        expenseCategory = em.persistFlushFind(Category.builder()
                .name("Otros egresos").icon("circle").color("#9CA3AF")
                .type(CategoryType.EXPENSE).isDefault(true)
                .build());

        from = LocalDate.of(2026, 7, 1);
        to = LocalDate.of(2026, 7, 31);
    }

    private InvestmentAsset persistAsset(String name) {
        return em.persistFlushFind(InvestmentAsset.builder()
                .user(user).name(name).type(InvestmentAssetType.LETRA)
                .currency("ARS").principal(new BigDecimal("100000.0000"))
                .purchaseDate(from).tna(BigDecimal.ZERO)
                .build());
    }

    private Transaction.TransactionBuilder baseTx() {
        return Transaction.builder()
                .user(user).description("Suscripción inversión: Test")
                .amount(new BigDecimal("100000.0000")).ccy("ARS")
                .transactionDate(from).dueDate(from)
                .type(TransactionType.EXPENSE)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Account persistAccount() {
        return em.persistFlushFind(Account.builder()
                .user(user).name("Cuenta Test").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private CreditCard persistCard() {
        return em.persistFlushFind(CreditCard.builder()
                .user(user).bank("Galicia").network("Visa").last4("1234").ccy("ARS")
                .creditLimit(new BigDecimal("500000.0000")).closingDay(10).dueDay(20).accent("indigo")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private Transaction.TransactionBuilder baseAccountTx(Account account, TransactionType type, BigDecimal amount) {
        return Transaction.builder()
                .user(user).account(account).description("Movimiento test")
                .amount(amount).ccy("ARS")
                .transactionDate(from).dueDate(from)
                .type(type)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Transaction.TransactionBuilder baseCardTx(CreditCard card, LocalDate dueDate, boolean paid) {
        return Transaction.builder()
                .user(user).card(card).description("Cuota test")
                .amount(new BigDecimal("10000.0000")).ccy("ARS")
                .transactionDate(dueDate.minusMonths(1)).dueDate(dueDate)
                .type(TransactionType.EXPENSE).paid(paid)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("incluye SUSCRIPCION con activo vivo")
    void includesLiveAssetSuscripcion() {
        InvestmentAsset asset = persistAsset("Letra viva");
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvestmentAsset().getName()).isEqualTo("Letra viva");
    }

    @Test
    @DisplayName("incluye SUSCRIPCION huérfana (activo borrado) sin categoría asignada — la que estaba invisible")
    void includesOrphanWithoutCategory() {
        em.persistAndFlush(baseTx().investmentAsset(null).category(null)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvestmentAsset()).isNull();
    }

    @Test
    @DisplayName("excluye SUSCRIPCION huérfana que ya tiene categoría asignada — evita el doble conteo con groupByCategory")
    void excludesOrphanWithCategory() {
        em.persistAndFlush(baseTx().investmentAsset(null).category(expenseCategory)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("excluye transacciones con soft-delete (deletedAt seteado)")
    void excludesSoftDeleted() {
        InvestmentAsset asset = persistAsset("Letra borrada");
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("excluye transacciones fuera del rango de fechas")
    void excludesOutOfDateRange() {
        InvestmentAsset asset = persistAsset("Letra fuera de rango");
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .transactionDate(LocalDate.of(2026, 6, 15)).dueDate(LocalDate.of(2026, 6, 15))
                .build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("excluye investmentSourceType no reconocido (p. ej. REVALUO, que nunca genera Transaction, o null)")
    void excludesUnrecognizedSourceType() {
        InvestmentAsset asset = persistAsset("Letra con tipo raro");
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentSourceType(null).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("excluye transacciones de cuentas con includeInCashflow=false")
    void excludesAccountNotIncludedInCashflow() {
        InvestmentAsset asset = persistAsset("Letra en cuenta excluida");
        Account excludedAccount = em.persistFlushFind(Account.builder()
                .user(user).name("Cuenta excluida").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        em.persistAndFlush(baseTx().investmentAsset(asset).account(excludedAccount)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("incluye transacciones de inversión sin cuenta (principal de Plazo Fijo, cobros) pese al filtro de includeInCashflow")
    void includesInvestmentTransactionsWithoutAccount() {
        InvestmentAsset asset = persistAsset("Letra sin cuenta");
        em.persistAndFlush(baseTx().investmentAsset(asset).account(null)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findInvestmentTransactionsForCashflow(user.getId(), from, to);

        assertThat(result).hasSize(1);
    }

    // ─── groupByCategory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("groupByCategory: suma y agrupa por categoría (sin orden garantizado por SQL — el orden pasa al servicio)")
    void groupByCategory_sumsAndGroupsCorrectly() {
        Account account = persistAccount();
        Category otherExpense = em.persistFlushFind(Category.builder()
                .name("Hogar").icon("home").color("#84CC16").type(CategoryType.EXPENSE).isDefault(true).build());

        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("200.0000"))
                .category(expenseCategory).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("50.0000"))
                .category(otherExpense).build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).hasSize(2);
        TransactionRepository.CategorySummaryProjection otrosEgresos = result.stream()
                .filter(r -> "Otros egresos".equals(r.getCategoryName())).findFirst().orElseThrow();
        TransactionRepository.CategorySummaryProjection hogar = result.stream()
                .filter(r -> "Hogar".equals(r.getCategoryName())).findFirst().orElseThrow();
        assertThat(otrosEgresos.getTotalAmount()).isEqualByComparingTo("300.0000");
        assertThat(hogar.getTotalAmount()).isEqualByComparingTo("50.0000");
    }

    @Test
    @DisplayName("groupByCategory: bimonetario — separa la misma categoría en filas distintas por ccy, sin mezclar ARS y USD")
    void groupByCategory_separatesByCurrency() {
        Account account = persistAccount();

        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("10000.0000"))
                .category(expenseCategory).ccy("ARS").build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("10.0000"))
                .category(expenseCategory).ccy("USD").build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.INCOME, from, to);

        assertThat(result).hasSize(2);
        TransactionRepository.CategorySummaryProjection arsRow = result.stream()
                .filter(r -> "ARS".equals(r.getCcy())).findFirst().orElseThrow();
        TransactionRepository.CategorySummaryProjection usdRow = result.stream()
                .filter(r -> "USD".equals(r.getCcy())).findFirst().orElseThrow();
        // Regresión directa del bug: los 10 USD nunca deben terminar sumados a los 10.000 ARS.
        assertThat(arsRow.getTotalAmount()).isEqualByComparingTo("10000.0000");
        assertThat(usdRow.getTotalAmount()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("groupByCategory: excluye transacciones de cuentas con includeInCashflow=false")
    void groupByCategory_excludesAccountNotIncludedInCashflow() {
        Account excludedAccount = em.persistFlushFind(Account.builder()
                .user(user).name("Cuenta excluida").kind("Banco").ccy("ARS")
                .balance(new BigDecimal("0.0000")).includeInCashflow(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        em.persistAndFlush(baseAccountTx(excludedAccount, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("groupByCategory: sigue incluyendo consumos de tarjeta sin cuenta (regresión del filtro includeInCashflow)")
    void groupByCategory_stillIncludesCardTransactionsWithoutAccount() {
        CreditCard card = persistCard();
        Transaction cuota = Transaction.builder()
                .user(user).card(card).description("Cuota").category(expenseCategory)
                .amount(new BigDecimal("100.0000")).ccy("ARS")
                .transactionDate(from).dueDate(from)
                .type(TransactionType.EXPENSE)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        em.persistAndFlush(cuota);

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("groupByCategory: excluye transacciones vinculadas a un activo de inversión aunque tengan categoría")
    void groupByCategory_excludesInvestmentLinkedTransactions() {
        Account account = persistAccount();
        InvestmentAsset asset = persistAsset("Letra viva");
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("groupByCategory: agrupa transacciones sin categoría en una fila \"Sin categoría\" en vez de excluirlas")
    void groupByCategory_uncategorizedTransactionsFormOwnRow() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(null).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("50.0000"))
                .category(null).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("20.0000"))
                .category(expenseCategory).build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).hasSize(2);
        TransactionRepository.CategorySummaryProjection uncategorized = result.stream()
                .filter(r -> r.getCategoryId() == null).findFirst().orElseThrow();
        assertThat(uncategorized.getCategoryName()).isEqualTo("Sin categoría");
        assertThat(uncategorized.getTotalAmount()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("groupByCategory: excluye legs de transferencias (transferGroupId no nulo)")
    void groupByCategory_excludesTransferLegs() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).transferGroupId(UUID.randomUUID()).build());

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("groupByCategory: para transacciones de tarjeta el período se determina por dueDate, no transactionDate")
    void groupByCategory_cardTransactionsUseDueDateForPeriod() {
        CreditCard card = persistCard();
        Transaction cuota = Transaction.builder()
                .user(user).card(card).description("Cuota").category(expenseCategory)
                .amount(new BigDecimal("100.0000")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 6, 15)).dueDate(LocalDate.of(2026, 7, 10))
                .type(TransactionType.EXPENSE)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        em.persistAndFlush(cuota);

        List<TransactionRepository.CategorySummaryProjection> result =
                transactionRepository.groupByCategory(user.getId(), TransactionType.EXPENSE, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo("100.0000");
    }

    // ─── findCardDebt ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findCardDebt: sólo cuotas impagas con dueDate desde la fecha dada")
    void findCardDebt_returnsUnpaidFutureCardTransactions() {
        CreditCard card = persistCard();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 8, 10), false).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 8, 10), true).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 5, 10), false).build());

        List<Transaction> result = transactionRepository.findCardDebt(user.getId(), LocalDate.of(2026, 7, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    // ─── search (paginado con filtros) ────────────────────────────────────────

    @Test
    @DisplayName("search: filtra por tipo, categoría y texto, y pagina")
    void search_filtersByTypeCategoryAndTextAndPaginates() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).description("Supermercado Coto").build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("500.0000"))
                .category(expenseCategory).description("Sueldo").build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("50.0000"))
                .category(expenseCategory).description("Farmacia").build());

        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> result = transactionRepository.search(
                user.getId(), from, to, TransactionType.EXPENSE, expenseCategory.getId(), "coto", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Supermercado Coto");
    }

    // ─── sumByType ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sumByType: suma sólo el tipo pedido, excluye transferencias")
    void sumByType_sumsMatchingExcludesTransfersAndAppliesCategoryFilter() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("200.0000"))
                .category(expenseCategory).transferGroupId(UUID.randomUUID()).build());

        BigDecimal result = transactionRepository.sumByType(
                user.getId(), TransactionType.EXPENSE, from, to, null, null);

        assertThat(result).isEqualByComparingTo("100.0000");
    }

    // ─── countFiltered ────────────────────────────────────────────────────────

    @Test
    @DisplayName("countFiltered: cuenta sólo las filas que matchean los filtros")
    void countFiltered_countsMatchingRows() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .category(expenseCategory).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("500.0000"))
                .category(expenseCategory).build());

        long result = transactionRepository.countFiltered(
                user.getId(), from, to, TransactionType.EXPENSE, null, null);

        assertThat(result).isEqualTo(1);
    }

    // ─── netMovementsForAccount / netMovementsForAccounts ────────────────────

    @Test
    @DisplayName("netMovementsForAccount: INCOME suma positivo, EXPENSE resta, hasta la fecha dada")
    void netMovementsForAccount_incomePositiveExpenseNegativeUpToDate() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("1000.0000")).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("300.0000")).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("9999.0000"))
                .transactionDate(LocalDate.of(2026, 8, 1)).dueDate(LocalDate.of(2026, 8, 1)).build());

        BigDecimal result = transactionRepository.netMovementsForAccount(user.getId(), account.getId(), to);

        assertThat(result).isEqualByComparingTo("700.0000");
    }

    @Test
    @DisplayName("netMovementsForAccounts: calcula el neto por cuenta para varias cuentas en una sola query")
    void netMovementsForAccounts_groupsMultipleAccounts() {
        Account accountA = persistAccount();
        Account accountB = persistAccount();
        em.persistAndFlush(baseAccountTx(accountA, TransactionType.INCOME, new BigDecimal("1000.0000")).build());
        em.persistAndFlush(baseAccountTx(accountB, TransactionType.EXPENSE, new BigDecimal("400.0000")).build());

        List<TransactionRepository.NetMovementProjection> result = transactionRepository.netMovementsForAccounts(
                user.getId(), List.of(accountA.getId(), accountB.getId()), to);

        assertThat(result).hasSize(2);
        assertThat(result.stream().filter(r -> r.getAccountId().equals(accountA.getId())).findFirst().get().getNetAmount())
                .isEqualByComparingTo("1000.0000");
        assertThat(result.stream().filter(r -> r.getAccountId().equals(accountB.getId())).findFirst().get().getNetAmount())
                .isEqualByComparingTo("-400.0000");
    }

    // ─── findUnpaidCardTransactionsForPeriod ──────────────────────────────────

    @Test
    @DisplayName("findUnpaidCardTransactionsForPeriod: sólo impagas de esa tarjeta dentro del rango de dueDate")
    void findUnpaidCardTransactionsForPeriod_returnsUnpaidWithinRangeOrderedByDueDate() {
        CreditCard card = persistCard();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 20), false).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 10), false).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 15), true).build());

        List<Transaction> result = transactionRepository.findUnpaidCardTransactionsForPeriod(
                user.getId(), card.getId(), from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    // ─── findCardTransactionsFromDate ─────────────────────────────────────────

    @Test
    @DisplayName("findCardTransactionsFromDate: incluye pagadas e impagas desde la fecha dada")
    void findCardTransactionsFromDate_includesPaidAndUnpaidFromDate() {
        CreditCard card = persistCard();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 20), true).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 10), false).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 5, 10), false).build());

        List<Transaction> result = transactionRepository.findCardTransactionsFromDate(user.getId(), from);

        assertThat(result).hasSize(2);
    }

    // ─── findCardPaymentTransactions / findByCardPaymentId / findExtraChargesForCard ─

    @Test
    @DisplayName("findCardPaymentTransactions: sólo las transacciones referenciadas como cardPaymentId de otra")
    void findCardPaymentTransactions_returnsRowsReferencedAsCardPaymentId() {
        Account account = persistAccount();
        Transaction payment = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("1000.0000")).build());
        em.persistAndFlush(baseCardTx(persistCard(), LocalDate.of(2026, 7, 10), true)
                .cardPaymentId(payment.getId()).build());

        List<Transaction> result = transactionRepository.findCardPaymentTransactions(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(payment.getId());
    }

    @Test
    @DisplayName("findByCardPaymentId: cuotas y extras vinculados a un pago, ordenados por monto descendente")
    void findByCardPaymentId_returnsRowsOrderedByAmountDesc() {
        UUID paymentId = UUID.randomUUID();
        CreditCard card = persistCard();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 10), true)
                .cardPaymentId(paymentId).amount(new BigDecimal("500.0000")).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 10), true)
                .cardPaymentId(paymentId).amount(new BigDecimal("1500.0000")).build());

        List<Transaction> result = transactionRepository.findByCardPaymentId(paymentId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("findExtraChargesForCard: cargos sin tarjeta vinculados a un pago de esa tarjeta específica")
    void findExtraChargesForCard_returnsChargesLinkedToCardPaymentsWithoutCard() {
        CreditCard card = persistCard();
        UUID paymentId = UUID.randomUUID();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 7, 10), true).cardPaymentId(paymentId).build());
        Transaction extra = Transaction.builder()
                .user(user).card(null).cardPaymentId(paymentId).description("Interés financiación")
                .amount(new BigDecimal("50.0000")).ccy("ARS")
                .transactionDate(from).dueDate(from).type(TransactionType.EXPENSE)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        em.persistAndFlush(extra);

        List<Transaction> result = transactionRepository.findExtraChargesForCard(card.getId(), from);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Interés financiación");
    }

    // ─── softDeleteByTransferGroupId ──────────────────────────────────────────

    @Test
    @DisplayName("softDeleteByTransferGroupId: marca deletedAt en ambas legs del usuario dueño")
    void softDeleteByTransferGroupId_softDeletesBothLegsForOwner() {
        Account account = persistAccount();
        UUID groupId = UUID.randomUUID();
        Transaction legOut = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transferGroupId(groupId).build());
        Transaction legIn = em.persistFlushFind(baseAccountTx(account, TransactionType.INCOME, new BigDecimal("100.0000"))
                .transferGroupId(groupId).build());

        int updated = transactionRepository.softDeleteByTransferGroupId(groupId, user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        em.getEntityManager().clear();

        assertThat(updated).isEqualTo(2);
        assertThat(transactionRepository.findById(legOut.getId()).get().getDeletedAt()).isNotNull();
        assertThat(transactionRepository.findById(legIn.getId()).get().getDeletedAt()).isNotNull();
    }

    // ─── métodos derivados por nombre (sin @Query) ────────────────────────────
    // Mecánicos y de bajo riesgo comparado con el JPQL escrito a mano, pero sin cobertura hasta ahora.

    @Test
    @DisplayName("findByIdAndDeletedAtIsNull: no encuentra una transacción soft-deleted")
    void findByIdAndDeletedAtIsNull_excludesSoftDeleted() {
        Account account = persistAccount();
        Transaction tx = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        assertThat(transactionRepository.findByIdAndDeletedAtIsNull(tx.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndDeletedAtIsNull: encuentra una transacción activa")
    void findByIdAndDeletedAtIsNull_returnsActive() {
        Account account = persistAccount();
        Transaction tx = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000")).build());

        assertThat(transactionRepository.findByIdAndDeletedAtIsNull(tx.getId()))
                .isPresent().get().extracting(Transaction::getId).isEqualTo(tx.getId());
    }

    @Test
    @DisplayName("findAllByInvestmentAsset_IdAndDeletedAtIsNull: sólo transacciones activas vinculadas a ese activo")
    void findAllByInvestmentAssetId_returnsOnlyActiveForThatAsset() {
        InvestmentAsset asset = persistAsset("Letra viva");
        InvestmentAsset otherAsset = persistAsset("Otra letra");
        Transaction active = em.persistFlushFind(baseTx().investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.persistAndFlush(baseTx().investmentAsset(otherAsset).investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        List<Transaction> result = transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(asset.getId());

        assertThat(result).extracting(Transaction::getId).containsExactly(active.getId());
    }

    @Test
    @DisplayName("findByInvestmentMovement_IdAndDeletedAtIsNull: no encuentra la transacción si está soft-deleted")
    void findByInvestmentMovementId_excludesSoftDeleted() {
        InvestmentAsset asset = persistAsset("Letra viva");
        InvestmentMovement movement = em.persistFlushFind(
                InvestmentMovement.builder()
                        .investmentAsset(asset).movementDate(from)
                        .type(InvestmentMovementType.SUSCRIPCION)
                        .amount(new BigDecimal("100000.0000")).build());
        em.persistAndFlush(baseTx().investmentAsset(asset).investmentMovement(movement)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        assertThat(transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movement.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByInvestmentMovement_IdAndDeletedAtIsNull: encuentra la transacción activa vinculada al movimiento")
    void findByInvestmentMovementId_returnsActive() {
        InvestmentAsset asset = persistAsset("Letra viva");
        InvestmentMovement movement = em.persistFlushFind(
                InvestmentMovement.builder()
                        .investmentAsset(asset).movementDate(from)
                        .type(InvestmentMovementType.SUSCRIPCION)
                        .amount(new BigDecimal("100000.0000")).build());
        Transaction tx = em.persistFlushFind(baseTx().investmentAsset(asset).investmentMovement(movement)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION).build());

        assertThat(transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movement.getId()))
                .isPresent().get().extracting(Transaction::getId).isEqualTo(tx.getId());
    }

    @Test
    @DisplayName("deleteByUserAndTransactionDateBetweenAndIsProjectedTrue: borra sólo las proyectadas del usuario dentro del rango")
    void deleteByUserAndTransactionDateBetweenAndIsProjectedTrue_deletesOnlyMatching() {
        Account account = persistAccount();
        Transaction projectedInRange = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .isProjected(true).build());
        Transaction realInRange = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("200.0000"))
                .isProjected(false).build());
        Transaction projectedOutOfRange = em.persistFlushFind(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("300.0000"))
                .isProjected(true).transactionDate(LocalDate.of(2026, 8, 1)).dueDate(LocalDate.of(2026, 8, 1)).build());

        transactionRepository.deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(user, from, to);
        em.getEntityManager().flush();
        em.getEntityManager().clear();

        assertThat(transactionRepository.findById(projectedInRange.getId())).isEmpty();
        assertThat(transactionRepository.findById(realInRange.getId())).isPresent();
        assertThat(transactionRepository.findById(projectedOutOfRange.getId())).isPresent();
    }

    @Test
    @DisplayName("findAllByInstallmentGroupIdAndDeletedAtIsNull: sólo las cuotas activas de ese grupo")
    void findAllByInstallmentGroupId_returnsOnlyActiveForThatGroup() {
        CreditCard card = persistCard();
        UUID groupId = UUID.randomUUID();
        UUID otherGroupId = UUID.randomUUID();
        Transaction cuota1 = em.persistFlushFind(baseCardTx(card, LocalDate.of(2026, 8, 10), false)
                .installmentGroupId(groupId).installmentNumber(1).totalInstallments(3).build());
        Transaction cuota2 = em.persistFlushFind(baseCardTx(card, LocalDate.of(2026, 9, 10), false)
                .installmentGroupId(groupId).installmentNumber(2).totalInstallments(3).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 10, 10), false)
                .installmentGroupId(groupId).installmentNumber(3).totalInstallments(3)
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 8, 10), false)
                .installmentGroupId(otherGroupId).installmentNumber(1).totalInstallments(1).build());

        List<Transaction> result = transactionRepository.findAllByInstallmentGroupIdAndDeletedAtIsNull(groupId);

        assertThat(result).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(cuota1.getId(), cuota2.getId());
    }

    // ─── findEarliestMovementDate (piso de navegación del cashflow) ────────────

    @Test
    @DisplayName("findEarliestMovementDate: devuelve null cuando el usuario no tiene movimientos")
    void findEarliestMovementDate_nullWhenNoMovements() {
        assertThat(transactionRepository.findEarliestMovementDate(user.getId())).isNull();
    }

    @Test
    @DisplayName("findEarliestMovementDate: para cuenta usa transactionDate como ancla (ignora dueDate)")
    void findEarliestMovementDate_accountUsesTransactionDate() {
        Account account = persistAccount();
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transactionDate(LocalDate.of(2026, 3, 15)).dueDate(LocalDate.of(2026, 1, 1)).build());

        assertThat(transactionRepository.findEarliestMovementDate(user.getId()))
                .isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("findEarliestMovementDate: para tarjeta usa dueDate como ancla (ignora transactionDate)")
    void findEarliestMovementDate_cardUsesDueDate() {
        CreditCard card = persistCard();
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 5, 10), false)
                .transactionDate(LocalDate.of(2026, 1, 1)).build());

        assertThat(transactionRepository.findEarliestMovementDate(user.getId()))
                .isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    @DisplayName("findEarliestMovementDate: toma el mínimo cruzando dueDate de tarjeta y transactionDate de cuenta")
    void findEarliestMovementDate_minAcrossCardAndAccount() {
        Account account = persistAccount();
        CreditCard card = persistCard();
        // Cuenta: transactionDate marzo. Tarjeta: dueDate febrero (más antiguo → gana).
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transactionDate(LocalDate.of(2026, 3, 20)).dueDate(LocalDate.of(2026, 3, 20)).build());
        em.persistAndFlush(baseCardTx(card, LocalDate.of(2026, 2, 10), false).build());

        assertThat(transactionRepository.findEarliestMovementDate(user.getId()))
                .isEqualTo(LocalDate.of(2026, 2, 10));
    }

    @Test
    @DisplayName("findEarliestMovementDate: ignora proyectados y soft-deleted")
    void findEarliestMovementDate_ignoresProjectedAndDeleted() {
        Account account = persistAccount();
        // Proyectado en enero (debe ignorarse), soft-deleted en febrero (debe ignorarse),
        // real en abril (el que debe ganar).
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transactionDate(LocalDate.of(2026, 1, 5)).dueDate(LocalDate.of(2026, 1, 5))
                .isProjected(true).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transactionDate(LocalDate.of(2026, 2, 5)).dueDate(LocalDate.of(2026, 2, 5))
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.persistAndFlush(baseAccountTx(account, TransactionType.EXPENSE, new BigDecimal("100.0000"))
                .transactionDate(LocalDate.of(2026, 4, 5)).dueDate(LocalDate.of(2026, 4, 5)).build());

        assertThat(transactionRepository.findEarliestMovementDate(user.getId()))
                .isEqualTo(LocalDate.of(2026, 4, 5));
    }
}
