package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.TransferRequest;
import com.vectis.backend.dto.TransferResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.AccountRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService")
class TransferServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private TransferService transferService;

    private User user;
    private UUID userId;
    private Account sourceAccount;
    private Account destAccount;
    private UUID sourceId;
    private UUID destId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("test@vectis.com").fullName("Test").passwordHash("h").build();

        sourceId = UUID.randomUUID();
        destId   = UUID.randomUUID();
        today    = LocalDate.of(2026, 6, 21);

        sourceAccount = Account.builder()
                .id(sourceId).user(user).name("Galicia ARS").ccy("ARS")
                .kind("Banco").balance(BigDecimal.ZERO).build();

        destAccount = Account.builder()
                .id(destId).user(user).name("Mercado Pago").ccy("ARS")
                .kind("Digital").balance(BigDecimal.ZERO).build();
    }

    // ── create — misma moneda ─────────────────────────────────────────────────

    @Test
    @DisplayName("create — misma ccy: crea EXPENSE en origen e INCOME en destino con mismo transferGroupId")
    void create_sameCcy_createsTwoLinkedLegs() {
        given(accountRepository.findByIdAndUser_Id(sourceId, userId)).willReturn(Optional.of(sourceAccount));
        given(accountRepository.findByIdAndUser_Id(destId,   userId)).willReturn(Optional.of(destAccount));

        Transaction savedDebit  = buildTx(TransactionType.EXPENSE, sourceAccount, new BigDecimal("10000.0000"), "ARS");
        Transaction savedCredit = buildTx(TransactionType.INCOME,  destAccount,   new BigDecimal("10000.0000"), "ARS");
        given(transactionRepository.saveAll(any())).willReturn(List.of(savedDebit, savedCredit));

        TransferRequest req = new TransferRequest(sourceId, destId,
                new BigDecimal("10000"), null, today, "Recarga");

        TransferResponse res = transferService.create(req, user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());

        List<Transaction> legs = captor.getValue();
        assertThat(legs).hasSize(2);

        Transaction debit  = legs.get(0);
        Transaction credit = legs.get(1);

        assertThat(debit.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(debit.getAccount()).isEqualTo(sourceAccount);
        assertThat(debit.getAmount()).isEqualByComparingTo("10000.0000");
        assertThat(debit.getCcy()).isEqualTo("ARS");
        assertThat(debit.getTransferGroupId()).isNotNull();

        assertThat(credit.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(credit.getAccount()).isEqualTo(destAccount);
        assertThat(credit.getAmount()).isEqualByComparingTo("10000.0000");
        assertThat(credit.getCcy()).isEqualTo("ARS");
        assertThat(credit.getTransferGroupId()).isEqualTo(debit.getTransferGroupId());

        assertThat(res.sourceAccountId()).isEqualTo(sourceId);
        assertThat(res.destAccountId()).isEqualTo(destId);
        assertThat(res.sourceAmount()).isEqualByComparingTo("10000.0000");
        assertThat(res.destAmount()).isEqualByComparingTo("10000.0000");
        assertThat(res.sourceCcy()).isEqualTo("ARS");
        assertThat(res.destCcy()).isEqualTo("ARS");
    }

    // ── create — cross-currency ───────────────────────────────────────────────

    @Test
    @DisplayName("create — cross-ccy: destAmount se aplica con HALF_EVEN y ccy de la cuenta destino")
    void create_crossCcy_appliesDestAmount() {
        Account usdAccount = Account.builder()
                .id(destId).user(user).name("USD Account").ccy("USD")
                .kind("Digital").balance(BigDecimal.ZERO).build();

        given(accountRepository.findByIdAndUser_Id(sourceId, userId)).willReturn(Optional.of(sourceAccount));
        given(accountRepository.findByIdAndUser_Id(destId,   userId)).willReturn(Optional.of(usdAccount));

        Transaction savedDebit  = buildTx(TransactionType.EXPENSE, sourceAccount, new BigDecimal("95000.0000"), "ARS");
        Transaction savedCredit = buildTx(TransactionType.INCOME,  usdAccount,    new BigDecimal("100.0000"), "USD");
        given(transactionRepository.saveAll(any())).willReturn(List.of(savedDebit, savedCredit));

        TransferRequest req = new TransferRequest(sourceId, destId,
                new BigDecimal("95000"), new BigDecimal("100"), today, null);

        TransferResponse res = transferService.create(req, user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());

        Transaction credit = captor.getValue().get(1);
        assertThat(credit.getAmount()).isEqualByComparingTo("100.0000");
        assertThat(credit.getCcy()).isEqualTo("USD");

        assertThat(res.sourceCcy()).isEqualTo("ARS");
        assertThat(res.destCcy()).isEqualTo("USD");
        assertThat(res.destAmount()).isEqualByComparingTo("100.0000");
    }

    // ── create — cuenta no encontrada ─────────────────────────────────────────

    @Test
    @DisplayName("create — cuenta origen no encontrada retorna 404")
    void create_sourceNotFound_throws404() {
        given(accountRepository.findByIdAndUser_Id(sourceId, userId)).willReturn(Optional.empty());

        TransferRequest req = new TransferRequest(sourceId, destId,
                new BigDecimal("1000"), null, today, null);

        assertThatThrownBy(() -> transferService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("create — cuenta destino no encontrada retorna 404")
    void create_destNotFound_throws404() {
        given(accountRepository.findByIdAndUser_Id(sourceId, userId)).willReturn(Optional.of(sourceAccount));
        given(accountRepository.findByIdAndUser_Id(destId,   userId)).willReturn(Optional.empty());

        TransferRequest req = new TransferRequest(sourceId, destId,
                new BigDecimal("1000"), null, today, null);

        assertThatThrownBy(() -> transferService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── create — misma cuenta ─────────────────────────────────────────────────

    @Test
    @DisplayName("create — source == dest retorna 400")
    void create_sameAccount_throws400() {
        given(accountRepository.findByIdAndUser_Id(sourceId, userId)).willReturn(Optional.of(sourceAccount));

        TransferRequest req = new TransferRequest(sourceId, sourceId,
                new BigDecimal("1000"), null, today, null);

        assertThatThrownBy(() -> transferService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — groupId válido: llama softDelete y no lanza excepción")
    void delete_validGroupId_softDeletesBothLegs() {
        UUID groupId = UUID.randomUUID();
        given(transactionRepository.softDeleteByTransferGroupId(eq(groupId), eq(userId), any(OffsetDateTime.class)))
                .willReturn(2);

        transferService.delete(groupId, user);

        verify(transactionRepository).softDeleteByTransferGroupId(eq(groupId), eq(userId), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("delete — groupId inexistente retorna 404")
    void delete_unknownGroupId_throws404() {
        UUID groupId = UUID.randomUUID();
        given(transactionRepository.softDeleteByTransferGroupId(eq(groupId), eq(userId), any(OffsetDateTime.class)))
                .willReturn(0);

        assertThatThrownBy(() -> transferService.delete(groupId, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction buildTx(TransactionType type, Account account, BigDecimal amount, String ccy) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(type)
                .account(account)
                .amount(amount)
                .ccy(ccy)
                .description("Transferencia")
                .transactionDate(today)
                .dueDate(today)
                .transferGroupId(UUID.randomUUID())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
