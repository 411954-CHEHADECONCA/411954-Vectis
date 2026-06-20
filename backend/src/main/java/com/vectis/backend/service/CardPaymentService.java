package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardPaymentCuotaItem;
import com.vectis.backend.dto.CardPaymentExtraItem;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardPaymentService {

    private final CreditCardRepository  creditCardRepository;
    private final AccountRepository     accountRepository;
    private final CategoryRepository    categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public CardPaymentResponse pay(UUID cardId, CardPaymentRequest request, User user) {
        CreditCard card = creditCardRepository.findById(cardId)
                .orElseThrow(() -> new CreditCardNotFoundException(cardId));

        if (!card.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para registrar pagos de esta tarjeta", HttpStatus.FORBIDDEN);
        }

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new VectisException("La cuenta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
        }

        LocalDate from = LocalDate.of(request.periodYear(), request.periodMonth(), 1);
        LocalDate to   = from.withDayOfMonth(from.lengthOfMonth());

        List<Transaction> unpaid = transactionRepository.findUnpaidCardTransactionsForPeriod(
                user.getId(), cardId, from, to);

        if (unpaid.isEmpty()) {
            throw new VectisException(
                    "No hay cuotas pendientes de tarjeta para " + request.periodYear()
                    + "/" + String.format("%02d", request.periodMonth()),
                    HttpStatus.CONFLICT);
        }

        Category paymentCategory = null;
        if (request.paymentCategoryId() != null) {
            paymentCategory = categoryRepository.findById(request.paymentCategoryId()).orElse(null);
            if (paymentCategory != null && paymentCategory.getUser() != null
                    && !paymentCategory.getUser().getId().equals(user.getId())) {
                throw new VectisException("La categoría no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
            }
        }

        String cardLabel = card.getBank() + " ····" + card.getLast4();
        String raw = "Pago " + cardLabel + " "
                + request.periodYear() + "/" + String.format("%02d", request.periodMonth());
        String description = raw.length() > 200 ? raw.substring(0, 200) : raw;

        // Crear la transacción de pago PRIMERO para obtener su ID y vincularlo a las cuotas
        Transaction paymentTx = Transaction.builder()
                .user(user)
                .type(TransactionType.EXPENSE)
                .description(description)
                .amount(request.paidAmount())
                .ccy(account.getCcy())
                .category(paymentCategory)
                .account(account)
                .transactionDate(request.paidDate())
                .dueDate(request.paidDate())
                .installment(false)
                .build();
        Transaction saved = transactionRepository.save(paymentTx);

        // Marcar cuotas como pagadas y vincularlas al pago
        unpaid.forEach(t -> {
            t.setPaid(true);
            t.setCardPaymentId(saved.getId());
        });
        transactionRepository.saveAll(unpaid);

        List<ExtraChargeRequest> extras =
                request.extraCharges() == null ? List.of() : request.extraCharges();

        List<Transaction> extraTxs = new ArrayList<>(extras.size());
        for (ExtraChargeRequest extra : extras) {
            Category extraCat = null;
            UUID extraCatId = extra.categoryId();
            if (extraCatId != null) {
                extraCat = categoryRepository.findById(extraCatId).orElse(null);
                if (extraCat != null && extraCat.getUser() != null
                        && !extraCat.getUser().getId().equals(user.getId())) {
                    throw new VectisException("La categoría no pertenece al usuario autenticado", HttpStatus.FORBIDDEN);
                }
            }
            extraTxs.add(Transaction.builder()
                    .user(user)
                    .type(TransactionType.EXPENSE)
                    .description(extra.description())
                    .amount(extra.amount())
                    .ccy(account.getCcy())
                    .category(extraCat)
                    .account(account)
                    .transactionDate(request.paidDate())
                    .dueDate(request.paidDate())
                    .installment(false)
                    .cardPaymentId(saved.getId())
                    .build());
        }
        if (!extraTxs.isEmpty()) {
            transactionRepository.saveAll(extraTxs);
        }

        return new CardPaymentResponse(unpaid.size(), saved.getId(), extraTxs.size());
    }

    @Transactional(readOnly = true)
    public List<CardPaymentHistoryResponse> getPaymentHistory(UUID userId) {
        List<Transaction> paymentTxs = transactionRepository.findCardPaymentTransactions(userId);

        List<CardPaymentHistoryResponse> result = new ArrayList<>(paymentTxs.size());
        for (Transaction paymentTx : paymentTxs) {
            List<Transaction> linked = transactionRepository.findByCardPaymentId(paymentTx.getId());

            List<Transaction> cuotasTxs = linked.stream()
                    .filter(t -> t.getCard() != null)
                    .toList();
            List<Transaction> extrasTxs = linked.stream()
                    .filter(t -> t.getCard() == null)
                    .toList();

            if (cuotasTxs.isEmpty()) continue;

            CreditCard card = cuotasTxs.get(0).getCard();
            LocalDate firstDue = cuotasTxs.get(0).getDueDate();

            List<CardPaymentCuotaItem> cuotas = cuotasTxs.stream()
                    .map(t -> new CardPaymentCuotaItem(
                            baseDescription(t.getDescription()),
                            t.isInstallment()
                                    ? t.getInstallmentNumber() + "/" + t.getTotalInstallments()
                                    : null,
                            t.getAmount(),
                            t.getDueDate(),
                            t.getCategory() != null ? t.getCategory().getIcon() : null,
                            t.getCategory() != null ? t.getCategory().getColor() : null))
                    .toList();

            List<CardPaymentExtraItem> extras = extrasTxs.stream()
                    .map(t -> new CardPaymentExtraItem(
                            t.getDescription(),
                            t.getAmount(),
                            t.getCategory() != null ? t.getCategory().getIcon() : null,
                            t.getCategory() != null ? t.getCategory().getColor() : null))
                    .toList();

            result.add(CardPaymentHistoryResponse.builder()
                    .paymentTransactionId(paymentTx.getId())
                    .cardId(card.getId())
                    .cardName(card.getBank() + " ····" + card.getLast4())
                    .periodYear(firstDue.getYear())
                    .periodMonth(firstDue.getMonthValue())
                    .paidDate(paymentTx.getTransactionDate())
                    .paidAmount(paymentTx.getAmount())
                    .ccy(paymentTx.getCcy())
                    .cuotas(cuotas)
                    .extraCharges(extras)
                    .build());
        }
        return result;
    }

    private String baseDescription(String description) {
        int idx = description.indexOf(" — cuota ");
        return idx >= 0 ? description.substring(0, idx) : description;
    }
}
