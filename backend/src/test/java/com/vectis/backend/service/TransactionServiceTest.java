package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.MovementRequest;
import com.vectis.backend.dto.MovementResponse;
import com.vectis.backend.dto.MovementSummaryResponse;
import com.vectis.backend.dto.PageResponse;
import com.vectis.backend.exception.TransactionNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.TransactionMapper;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @InjectMocks private TransactionService transactionService;

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CreditCardRepository creditCardRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private InstallmentCalculator installmentCalculator;

    private User user;
    private User otherUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash")
                .build();
        otherUser = User.builder()
                .id(UUID.randomUUID()).email("other@vectis.com").fullName("Other").passwordHash("hash")
                .build();
    }

    // ─── create simple ────────────────────────────────────────────────────────

    @Test
    @DisplayName("create de pago único setea dueDate = transactionDate y no es cuota")
    void create_singlePayment_setsDueDateAndNotInstallment() {
        MovementRequest req = new MovementRequest(
                "Sueldo", new BigDecimal("1240000"), "ARS", "INCOME",
                null, null, null, LocalDate.of(2026, 6, 10), 1);

        given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> inv.getArgument(0));
        given(transactionMapper.toResponse(any(Transaction.class))).willReturn(mock());

        List<MovementResponse> result = transactionService.create(req, user);

        assertThat(result).hasSize(1);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.isInstallment()).isFalse();
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(saved.getUser().getId()).isEqualTo(userId);
    }

    // ─── create con cuotas ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create con cuotas genera N filas con installmentGroupId común")
    void create_installments_generatesNRowsWithGroup() {
        UUID cardId = UUID.randomUUID();
        CreditCard card = card(cardId, user);
        MovementRequest req = new MovementRequest(
                "Notebook", new BigDecimal("60000"), "ARS", "EXPENSE",
                null, null, cardId, LocalDate.of(2026, 4, 7), 3);

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));
        given(installmentCalculator.split(any(), any(), anyInt(), anyInt(), anyInt()))
                .willReturn(List.of(
                        new InstallmentCalculator.Installment(1, LocalDate.of(2026, 5, 15), new BigDecimal("20000")),
                        new InstallmentCalculator.Installment(2, LocalDate.of(2026, 6, 15), new BigDecimal("20000")),
                        new InstallmentCalculator.Installment(3, LocalDate.of(2026, 7, 15), new BigDecimal("20000"))));
        given(transactionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(transactionMapper.toResponse(any(Transaction.class))).willReturn(mock());

        List<MovementResponse> result = transactionService.create(req, user);

        assertThat(result).hasSize(3);
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<Transaction> saved = captor.getValue();
        assertThat(saved).allMatch(Transaction::isInstallment);
        assertThat(saved).extracting(Transaction::getInstallmentGroupId).containsOnly(saved.get(0).getInstallmentGroupId());
        assertThat(saved.get(0).getInstallmentGroupId()).isNotNull();
        assertThat(saved).extracting(Transaction::getTotalInstallments).containsOnly(3);
    }

    @Test
    @DisplayName("create con cuotas sin tarjeta lanza BAD_REQUEST")
    void create_installmentsWithoutCard_throwsBadRequest() {
        MovementRequest req = new MovementRequest(
                "Notebook", new BigDecimal("60000"), "ARS", "EXPENSE",
                null, null, null, LocalDate.of(2026, 4, 7), 3);

        assertThatThrownBy(() -> transactionService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create con cuotas en INCOME lanza BAD_REQUEST")
    void create_installmentsOnIncome_throwsBadRequest() {
        UUID cardId = UUID.randomUUID();
        CreditCard card = card(cardId, user);
        MovementRequest req = new MovementRequest(
                "Algo", new BigDecimal("60000"), "ARS", "INCOME",
                null, null, cardId, LocalDate.of(2026, 4, 7), 3);

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> transactionService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create con cuenta y tarjeta simultáneas lanza BAD_REQUEST")
    void create_bothAccountAndCard_throwsBadRequest() {
        MovementRequest req = new MovementRequest(
                "Algo", new BigDecimal("1000"), "ARS", "EXPENSE",
                null, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 6, 10), 1);

        assertThatThrownBy(() -> transactionService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("create con tarjeta de otro usuario lanza FORBIDDEN")
    void create_cardNotOwned_throwsForbidden() {
        UUID cardId = UUID.randomUUID();
        CreditCard otherCard = card(cardId, otherUser);
        MovementRequest req = new MovementRequest(
                "Algo", new BigDecimal("1000"), "ARS", "EXPENSE",
                null, null, cardId, LocalDate.of(2026, 6, 10), 1);

        given(creditCardRepository.findById(cardId)).willReturn(Optional.of(otherCard));

        assertThatThrownBy(() -> transactionService.create(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── update ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update de una cuota lanza BAD_REQUEST")
    void update_installmentRow_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        Transaction tx = simpleTx(user);
        tx.setInstallment(true);
        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(tx));

        MovementRequest req = new MovementRequest(
                "X", new BigDecimal("1000"), "ARS", "EXPENSE", null, null, null, LocalDate.of(2026, 6, 10), 1);

        assertThatThrownBy(() -> transactionService.update(id, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("update de movimiento de otro usuario lanza FORBIDDEN")
    void update_notOwner_throwsForbidden() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(simpleTx(otherUser)));

        MovementRequest req = new MovementRequest(
                "X", new BigDecimal("1000"), "ARS", "EXPENSE", null, null, null, LocalDate.of(2026, 6, 10), 1);

        assertThatThrownBy(() -> transactionService.update(id, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("update inexistente lanza NOT_FOUND")
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

        MovementRequest req = new MovementRequest(
                "X", new BigDecimal("1000"), "ARS", "EXPENSE", null, null, null, LocalDate.of(2026, 6, 10), 1);

        assertThatThrownBy(() -> transactionService.update(id, req, user))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    // ─── delete ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete de un movimiento simple soft-deletea solo esa fila")
    void delete_single_softDeletesRow() {
        UUID id = UUID.randomUUID();
        Transaction tx = simpleTx(user);
        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(tx));

        transactionService.delete(id, user);

        assertThat(tx.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("delete de una cuota soft-deletea todo el grupo")
    void delete_installment_softDeletesWholeGroup() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Transaction tx = simpleTx(user);
        tx.setInstallment(true);
        tx.setInstallmentGroupId(groupId);

        Transaction other = simpleTx(user);
        other.setInstallment(true);
        other.setInstallmentGroupId(groupId);

        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(tx));
        given(transactionRepository.findAllByInstallmentGroupIdAndDeletedAtIsNull(groupId))
                .willReturn(List.of(tx, other));

        transactionService.delete(id, user);

        assertThat(tx.getDeletedAt()).isNotNull();
        assertThat(other.getDeletedAt()).isNotNull();
        verify(transactionRepository).saveAll(List.of(tx, other));
    }

    // ─── search / summary ───────────────────────────────────────────────────────

    @Test
    @DisplayName("search mapea la página y normaliza filtros vacíos a null")
    void search_mapsPageAndNormalizesFilters() {
        Transaction tx = simpleTx(user);
        given(transactionRepository.search(eq(userId), any(), any(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(tx)));
        given(transactionMapper.toResponse(tx)).willReturn(mock());

        PageResponse<MovementResponse> result = transactionService.search(
                userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "  ", null, "", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("summary calcula ingresos, egresos y neto del período")
    void summary_computesNet() {
        given(transactionRepository.sumByType(eq(userId), eq("INCOME"), any(), any(), any(), any()))
                .willReturn(new BigDecimal("1000"));
        given(transactionRepository.sumByType(eq(userId), eq("EXPENSE"), any(), any(), any(), any()))
                .willReturn(new BigDecimal("400"));
        given(transactionRepository.countFiltered(eq(userId), any(), any(), isNull(), any(), any()))
                .willReturn(5L);

        MovementSummaryResponse summary = transactionService.summary(
                userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null);

        assertThat(summary.totalIncome()).isEqualByComparingTo("1000");
        assertThat(summary.totalExpense()).isEqualByComparingTo("400");
        assertThat(summary.net()).isEqualByComparingTo("600");
        assertThat(summary.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("update de un movimiento simple propio actualiza los campos")
    void update_simple_updatesFields() {
        UUID id = UUID.randomUUID();
        Transaction tx = simpleTx(user);
        given(transactionRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(tx));
        given(transactionRepository.save(tx)).willReturn(tx);
        given(transactionMapper.toResponse(tx)).willReturn(mock());

        MovementRequest req = new MovementRequest(
                "Coto editado", new BigDecimal("99999"), "ARS", "EXPENSE",
                null, null, null, LocalDate.of(2026, 6, 12), 1);

        transactionService.update(id, req, user);

        assertThat(tx.getDescription()).isEqualTo("Coto editado");
        assertThat(tx.getAmount()).isEqualByComparingTo("99999");
        assertThat(tx.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        verify(transactionRepository).save(tx);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private MovementResponse mock() {
        return MovementResponse.builder().id(UUID.randomUUID()).description("x").build();
    }

    private Transaction simpleTx(User owner) {
        return Transaction.builder()
                .id(UUID.randomUUID()).user(owner).type("EXPENSE").description("Coto")
                .amount(new BigDecimal("1000")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 6, 10)).dueDate(LocalDate.of(2026, 6, 10))
                .installment(false).createdAt(OffsetDateTime.now())
                .build();
    }

    private CreditCard card(UUID id, User owner) {
        return CreditCard.builder()
                .id(id).user(owner).bank("Galicia").network("Visa").last4("4821")
                .ccy("ARS").creditLimit(BigDecimal.TEN).closingDay(5).dueDay(15).accent("#52eacd")
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
    }
}
