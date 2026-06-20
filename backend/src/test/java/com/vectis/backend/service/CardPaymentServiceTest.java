package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardPaymentHistoryResponse;
import com.vectis.backend.dto.CardPaymentRequest;
import com.vectis.backend.dto.CardPaymentResponse;
import com.vectis.backend.dto.ExtraChargeRequest;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.CreditCardNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryRepository;
import com.vectis.backend.repository.CreditCardRepository;
import com.vectis.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardPaymentService")
class CardPaymentServiceTest {

    @InjectMocks CardPaymentService cardPaymentService;

    @Mock CreditCardRepository  creditCardRepository;
    @Mock AccountRepository     accountRepository;
    @Mock CategoryRepository    categoryRepository;
    @Mock TransactionRepository transactionRepository;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID cardId;
    private UUID accountId;
    private CreditCard card;
    private Account account;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        cardId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        user = User.builder().id(userId).email("u@v.com").fullName("User").passwordHash("h").build();
        otherUser = User.builder().id(UUID.randomUUID()).email("o@v.com").fullName("Other").passwordHash("h").build();

        card = CreditCard.builder()
                .id(cardId).user(user)
                .bank("Galicia").network("Visa").last4("1234")
                .ccy("ARS").creditLimit(new BigDecimal("500000"))
                .closingDay(15).dueDay(5).accent("#52eacd")
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        account = Account.builder()
                .id(accountId).user(user)
                .name("Caja ARS").ccy("ARS")
                .balance(new BigDecimal("200000"))
                .includeInCashflow(true)
                .build();
    }

    // ─── pay — cuotas marcadas como pagadas ───────────────────────────────────

    @Test
    @DisplayName("pay marca todas las cuotas del período como pagadas")
    void pay_marksCuotasAsPaid() {
        Transaction t1 = buildCardTx();
        Transaction t2 = buildCardTx();
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of(t1, t2));
        given(transactionRepository.saveAll(anyList())).willReturn(List.of(t1, t2));
        given(transactionRepository.save(any(Transaction.class))).willReturn(buildAccountTx());

        cardPaymentService.pay(cardId, buildRequest(), user);

        assertThat(t1.isPaid()).isTrue();
        assertThat(t2.isPaid()).isTrue();
        verify(transactionRepository).saveAll(List.of(t1, t2));
    }

    // ─── pay — crea transacción de cuenta EXPENSE ─────────────────────────────

    @Test
    @DisplayName("pay crea una transacción EXPENSE de cuenta para el pago principal")
    void pay_createsAccountExpenseTransaction() {
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of(buildCardTx()));
        given(transactionRepository.saveAll(anyList())).willReturn(List.of());
        given(transactionRepository.save(any(Transaction.class))).willReturn(buildAccountTx());

        cardPaymentService.pay(cardId, buildRequest(), user);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getAccount()).isEqualTo(account);
        assertThat(saved.getCard()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("82700.00"));
    }

    // ─── pay — crea transacciones extra ──────────────────────────────────────

    @Test
    @DisplayName("pay crea transacciones adicionales por cada cargo extra")
    void pay_withExtraCharges_createsExtraTransactions() {
        CardPaymentRequest request = new CardPaymentRequest(
                accountId, LocalDate.of(2026, 7, 25),
                new BigDecimal("85000.00"), 2026, 7, null,
                List.of(
                        new ExtraChargeRequest("Interés", new BigDecimal("2500.00"), null),
                        new ExtraChargeRequest("Mantenimiento", new BigDecimal("200.00"), null)
                )
        );

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of(buildCardTx()));
        given(transactionRepository.saveAll(anyList())).willReturn(List.of());
        given(transactionRepository.save(any(Transaction.class))).willReturn(buildAccountTx());

        CardPaymentResponse response = cardPaymentService.pay(cardId, request, user);

        // 1 save para el pago principal + 1 saveAll para los 2 cargos extra
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository, times(2)).saveAll(captor.capture());
        List<List<Transaction>> allSaveAllCalls = captor.getAllValues();
        // 1er saveAll: cuotas marcadas paid=true; 2do saveAll: extra charges
        assertThat(allSaveAllCalls.get(1)).hasSize(2);
        assertThat(response.extraChargesCreated()).isEqualTo(2);
    }

    // ─── pay — sin cuotas pendientes → 409 ───────────────────────────────────

    @Test
    @DisplayName("pay lanza 409 si no hay cuotas pendientes en el período")
    void pay_noUnpaidCuotas_throws409() {
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of());

        assertThatThrownBy(() -> cardPaymentService.pay(cardId, buildRequest(), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(transactionRepository, never()).save(any());
    }

    // ─── pay — tarjeta no encontrada → 404 ───────────────────────────────────

    @Test
    @DisplayName("pay lanza 404 si la tarjeta no existe")
    void pay_cardNotFound_throwsCreditCardNotFoundException() {
        given(creditCardRepository.findById(cardId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardPaymentService.pay(cardId, buildRequest(), user))
                .isInstanceOf(CreditCardNotFoundException.class);
    }

    // ─── pay — tarjeta de otro usuario → 403 ─────────────────────────────────

    @Test
    @DisplayName("pay lanza 403 si la tarjeta pertenece a otro usuario")
    void pay_cardNotOwned_throws403() {
        CreditCard otherCard = CreditCard.builder()
                .id(cardId).user(otherUser).bank("Banco").network("Visa").last4("9999")
                .ccy("ARS").creditLimit(BigDecimal.ZERO).closingDay(1).dueDay(1)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(otherCard));

        assertThatThrownBy(() -> cardPaymentService.pay(cardId, buildRequest(), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── pay — cuenta no encontrada → 404 ────────────────────────────────────

    @Test
    @DisplayName("pay lanza 404 si la cuenta no existe")
    void pay_accountNotFound_throwsAccountNotFoundException() {
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cardPaymentService.pay(cardId, buildRequest(), user))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ─── pay — cuenta de otro usuario → 403 ──────────────────────────────────

    @Test
    @DisplayName("pay lanza 403 si la cuenta pertenece a otro usuario")
    void pay_accountNotOwned_throws403() {
        Account otherAccount = Account.builder()
                .id(accountId).user(otherUser).name("Otra").ccy("ARS")
                .balance(BigDecimal.ZERO).includeInCashflow(true).build();
        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> cardPaymentService.pay(cardId, buildRequest(), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── pay — extraCharges null → sin NullPointerException ──────────────────

    @Test
    @DisplayName("pay con extraCharges null se trata como lista vacía sin excepción")
    void pay_nullExtraCharges_treatedAsEmpty() {
        CardPaymentRequest request = new CardPaymentRequest(
                accountId, LocalDate.of(2026, 7, 25),
                new BigDecimal("80000.00"), 2026, 7, null, null
        );

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of(buildCardTx()));
        given(transactionRepository.saveAll(anyList())).willReturn(List.of());
        given(transactionRepository.save(any(Transaction.class))).willReturn(buildAccountTx());

        CardPaymentResponse response = cardPaymentService.pay(cardId, request, user);

        assertThat(response.extraChargesCreated()).isZero();
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    // ─── pay — cardPaymentId en cuotas ───────────────────────────────────────

    @Test
    @DisplayName("pay vincula las cuotas al pago mediante cardPaymentId")
    void pay_setsCardPaymentIdOnCuotas() {
        Transaction t1 = buildCardTx();
        Transaction t2 = buildCardTx();
        Transaction savedPaymentTx = buildAccountTx();

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
        given(transactionRepository.findUnpaidCardTransactionsForPeriod(userId, cardId, periodStart(), periodEnd()))
                .willReturn(List.of(t1, t2));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedPaymentTx);
        given(transactionRepository.saveAll(anyList())).willReturn(List.of());

        cardPaymentService.pay(cardId, buildRequest(), user);

        assertThat(t1.getCardPaymentId()).isEqualTo(savedPaymentTx.getId());
        assertThat(t2.getCardPaymentId()).isEqualTo(savedPaymentTx.getId());
    }

    // ─── getPaymentHistory ────────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentHistory retorna historial con cuotas y cargos extra")
    void getPaymentHistory_returnsHistory() {
        UUID paymentId = UUID.randomUUID();
        Transaction paymentTx = Transaction.builder()
                .id(paymentId).user(user).account(account)
                .type(TransactionType.EXPENSE)
                .description("Pago Galicia ····1234 2026/07")
                .amount(new BigDecimal("82700.00")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 7, 25))
                .dueDate(LocalDate.of(2026, 7, 25))
                .installment(false).build();

        Transaction cuota = Transaction.builder()
                .id(UUID.randomUUID()).user(user).card(card)
                .type(TransactionType.EXPENSE)
                .description("Compra — cuota 1/3")
                .amount(new BigDecimal("1000.00")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 6, 10))
                .dueDate(LocalDate.of(2026, 7, 5))
                .installment(true).installmentNumber(1).totalInstallments(3)
                .paid(true).cardPaymentId(paymentId).build();

        Transaction extra = Transaction.builder()
                .id(UUID.randomUUID()).user(user).account(account)
                .type(TransactionType.EXPENSE).description("Interés")
                .amount(new BigDecimal("500.00")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 7, 25))
                .dueDate(LocalDate.of(2026, 7, 25))
                .installment(false).cardPaymentId(paymentId).build();

        given(transactionRepository.findCardPaymentTransactions(userId)).willReturn(List.of(paymentTx));
        given(transactionRepository.findByCardPaymentId(paymentId)).willReturn(List.of(cuota, extra));

        List<CardPaymentHistoryResponse> result = cardPaymentService.getPaymentHistory(userId);

        assertThat(result).hasSize(1);
        CardPaymentHistoryResponse h = result.get(0);
        assertThat(h.cardName()).isEqualTo("Galicia ····1234");
        assertThat(h.periodYear()).isEqualTo(2026);
        assertThat(h.periodMonth()).isEqualTo(7);
        assertThat(h.paidAmount()).isEqualByComparingTo(new BigDecimal("82700.00"));
        assertThat(h.ccy()).isEqualTo("ARS");
        assertThat(h.cuotas()).hasSize(1);
        assertThat(h.cuotas().get(0).description()).isEqualTo("Compra");
        assertThat(h.cuotas().get(0).installmentLabel()).isEqualTo("1/3");
        assertThat(h.extraCharges()).hasSize(1);
        assertThat(h.extraCharges().get(0).description()).isEqualTo("Interés");
    }

    @Test
    @DisplayName("getPaymentHistory sin pagos retorna lista vacía")
    void getPaymentHistory_noPagos_returnsEmptyList() {
        given(transactionRepository.findCardPaymentTransactions(userId)).willReturn(List.of());

        assertThat(cardPaymentService.getPaymentHistory(userId)).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CardPaymentRequest buildRequest() {
        return new CardPaymentRequest(
                accountId, LocalDate.of(2026, 7, 25),
                new BigDecimal("82700.00"), 2026, 7, null, List.of()
        );
    }

    private Transaction buildCardTx() {
        return Transaction.builder()
                .id(UUID.randomUUID()).user(user).card(card)
                .type(TransactionType.EXPENSE).description("Compra").amount(new BigDecimal("1000"))
                .ccy("ARS").transactionDate(LocalDate.of(2026, 6, 10))
                .dueDate(LocalDate.of(2026, 7, 5))
                .installment(false).paid(false).build();
    }

    private Transaction buildAccountTx() {
        return Transaction.builder()
                .id(UUID.randomUUID()).user(user).account(account)
                .type(TransactionType.EXPENSE).description("Pago Galicia ····1234 2026/07")
                .amount(new BigDecimal("82700.00")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 7, 25))
                .dueDate(LocalDate.of(2026, 7, 25))
                .installment(false).paid(false).build();
    }

    private LocalDate periodStart() { return LocalDate.of(2026, 7, 1); }
    private LocalDate periodEnd()   { return LocalDate.of(2026, 7, 31); }
}
