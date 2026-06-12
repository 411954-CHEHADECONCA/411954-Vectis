package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.MovementRequest;
import com.vectis.backend.dto.MovementResponse;
import com.vectis.backend.dto.MovementSummaryResponse;
import com.vectis.backend.dto.PageResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.CreditCardNotFoundException;
import com.vectis.backend.exception.TransactionNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.TransactionMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryRepository;
import com.vectis.backend.repository.CreditCardRepository;
import com.vectis.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardRepository creditCardRepository;
    private final TransactionMapper transactionMapper;
    private final InstallmentCalculator installmentCalculator;

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> search(UUID userId, LocalDate from, LocalDate to,
                                                 String type, UUID categoryId, String q,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> result = transactionRepository.search(
                userId, from, to, normalize(type), categoryId, normalize(q), pageable);
        List<MovementResponse> content = result.getContent().stream()
                .map(transactionMapper::toResponse)
                .toList();
        return PageResponse.of(result, content);
    }

    @Transactional(readOnly = true)
    public MovementSummaryResponse summary(UUID userId, LocalDate from, LocalDate to,
                                           String type, UUID categoryId, String q) {
        String t = normalize(type);
        String search = normalize(q);
        BigDecimal income = (t == null || t.equals("INCOME"))
                ? transactionRepository.sumByType(userId, "INCOME", from, to, categoryId, search)
                : BigDecimal.ZERO;
        BigDecimal expense = (t == null || t.equals("EXPENSE"))
                ? transactionRepository.sumByType(userId, "EXPENSE", from, to, categoryId, search)
                : BigDecimal.ZERO;
        long count = transactionRepository.countFiltered(userId, from, to, t, categoryId, search);
        return MovementSummaryResponse.builder()
                .totalIncome(income)
                .totalExpense(expense)
                .net(income.subtract(expense))
                .count(count)
                .build();
    }

    public List<MovementResponse> create(MovementRequest request, User user) {
        validateSinglePaymentMethod(request.accountId(), request.cardId());

        Category category = resolveCategory(request.categoryId());
        Account account   = resolveAccount(request.accountId(), user);
        CreditCard card   = resolveCard(request.cardId(), user);

        int installments = request.installments() == null ? 1 : request.installments();

        if (installments > 1) {
            return createInstallmentPlan(request, user, category, card);
        }

        Transaction tx = Transaction.builder()
                .user(user)
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .ccy(request.ccy())
                .category(category)
                .account(account)
                .card(card)
                .transactionDate(request.transactionDate())
                .dueDate(request.transactionDate())
                .installment(false)
                .build();

        return List.of(transactionMapper.toResponse(transactionRepository.save(tx)));
    }

    private List<MovementResponse> createInstallmentPlan(MovementRequest request, User user,
                                                         Category category, CreditCard card) {
        if (card == null) {
            throw new VectisException("Pagar en cuotas requiere una tarjeta de crédito", HttpStatus.BAD_REQUEST);
        }
        if (!"EXPENSE".equals(request.type())) {
            throw new VectisException("Solo los egresos pueden financiarse en cuotas", HttpStatus.BAD_REQUEST);
        }

        int n = request.installments();
        UUID groupId = UUID.randomUUID();
        List<InstallmentCalculator.Installment> parts = installmentCalculator.split(
                request.amount(), request.transactionDate(), card.getClosingDay(), card.getDueDay(), n);

        List<Transaction> toSave = new ArrayList<>(n);
        for (InstallmentCalculator.Installment part : parts) {
            toSave.add(Transaction.builder()
                    .user(user)
                    .type(request.type())
                    .description(request.description() + " — cuota " + part.number() + "/" + n)
                    .amount(part.amount())
                    .ccy(request.ccy())
                    .category(category)
                    .account(null)
                    .card(card)
                    .transactionDate(request.transactionDate())
                    .dueDate(part.dueDate())
                    .installment(true)
                    .installmentNumber(part.number())
                    .totalInstallments(n)
                    .installmentGroupId(groupId)
                    .build());
        }

        return transactionRepository.saveAll(toSave).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    public MovementResponse update(UUID id, MovementRequest request, User user) {
        Transaction tx = transactionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        requireOwnership(tx, user, "modificar");

        if (tx.isInstallment()) {
            throw new VectisException("Las cuotas no se editan individualmente; eliminá el grupo y volvé a cargarlo",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.installments() != null && request.installments() > 1) {
            throw new VectisException("No se puede convertir un movimiento existente en cuotas", HttpStatus.BAD_REQUEST);
        }

        validateSinglePaymentMethod(request.accountId(), request.cardId());

        Category category = resolveCategory(request.categoryId());
        Account account   = resolveAccount(request.accountId(), user);
        CreditCard card   = resolveCard(request.cardId(), user);

        tx.setType(request.type());
        tx.setDescription(request.description());
        tx.setAmount(request.amount());
        tx.setCcy(request.ccy());
        tx.setCategory(category);
        tx.setAccount(account);
        tx.setCard(card);
        tx.setTransactionDate(request.transactionDate());
        tx.setDueDate(request.transactionDate());

        return transactionMapper.toResponse(transactionRepository.save(tx));
    }

    public void delete(UUID id, User user) {
        Transaction tx = transactionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        requireOwnership(tx, user, "eliminar");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (tx.getInstallmentGroupId() != null) {
            // Eliminar todo el grupo de cuotas.
            List<Transaction> group = transactionRepository
                    .findAllByInstallmentGroupIdAndDeletedAtIsNull(tx.getInstallmentGroupId());
            group.forEach(t -> t.setDeletedAt(now));
            transactionRepository.saveAll(group);
        } else {
            tx.setDeletedAt(now);
            transactionRepository.save(tx);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireOwnership(Transaction tx, User user, String action) {
        if (!tx.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para " + action + " este movimiento", HttpStatus.FORBIDDEN);
        }
    }

    private void validateSinglePaymentMethod(UUID accountId, UUID cardId) {
        if (accountId != null && cardId != null) {
            throw new VectisException("No podés asociar cuenta y tarjeta al mismo tiempo", HttpStatus.BAD_REQUEST);
        }
    }

    private Category resolveCategory(UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new VectisException("Categoría no encontrada: " + categoryId, HttpStatus.NOT_FOUND));
    }

    private Account resolveAccount(UUID accountId, User user) {
        if (accountId == null) return null;
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("La cuenta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
        }
        return account;
    }

    private CreditCard resolveCard(UUID cardId, User user) {
        if (cardId == null) return null;
        CreditCard card = creditCardRepository.findById(cardId)
                .orElseThrow(() -> new CreditCardNotFoundException(cardId));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new VectisException("La tarjeta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
        }
        return card;
    }

    /** Convierte cadenas vacías/espacios a null para los filtros opcionales. */
    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
