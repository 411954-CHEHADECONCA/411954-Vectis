package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.dto.*;
import com.vectis.backend.repository.CreditCardRepository;
import com.vectis.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardProjectionService {

    private final CreditCardRepository creditCardRepository;
    private final TransactionRepository transactionRepository;
    private final CardCycle cardCycle;

    // ── Overview (caras + cuotas activas + vencimientos) ───────────────────────

    public CardOverviewResponse overview(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = YearMonth.from(today).atDay(1);

        List<CreditCard> cards = creditCardRepository.findAllByUser_IdOrderByCreatedAtAsc(userId);
        List<Transaction> debt = transactionRepository.findCardDebt(userId, monthStart);

        Map<UUID, List<Transaction>> byCard = groupBy(debt, t -> t.getCard().getId());

        List<CardFaceResponse> faces = new ArrayList<>();
        List<CardDueResponse> vencimientos = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;

        for (CreditCard card : cards) {
            List<Transaction> cardDebt = byCard.getOrDefault(card.getId(), List.of());
            BigDecimal consumido = sum(cardDebt);
            totalDue = totalDue.add(consumido);

            LocalDate nextClosing = cardCycle.nextClosingDate(card.getClosingDay(), today);
            LocalDate nextDue     = cardCycle.nextDueDate(card.getDueDay(), today);

            faces.add(CardFaceResponse.builder()
                    .id(card.getId())
                    .bank(card.getBank())
                    .network(card.getNetwork())
                    .last4(card.getLast4())
                    .ccy(card.getCcy())
                    .accent(card.getAccent())
                    .creditLimit(card.getCreditLimit())
                    .consumido(consumido)
                    .nextClosingDate(nextClosing)
                    .nextDueDate(nextDue)
                    .build());

            // Próximo vencimiento real: el primer mes con deuda. Monto = lo que vence ese mes.
            cardDebt.stream()
                    .map(Transaction::getDueDate)
                    .min(LocalDate::compareTo)
                    .ifPresent(firstDue -> {
                        String dueMonth = cardCycle.monthKey(firstDue);
                        BigDecimal dueAmount = cardDebt.stream()
                                .filter(t -> cardCycle.monthKey(t.getDueDate()).equals(dueMonth))
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        vencimientos.add(CardDueResponse.builder()
                                .cardId(card.getId())
                                .cardName(cardName(card))
                                .dueDate(firstDue)
                                .amount(dueAmount)
                                .build());
                    });
        }

        vencimientos.sort(Comparator.comparing(CardDueResponse::dueDate));
        CardDueResponse next = vencimientos.isEmpty() ? null : vencimientos.get(0);

        return CardOverviewResponse.builder()
                .cards(faces)
                .totalDue(totalDue)
                .nextDueCardName(next != null ? next.cardName() : null)
                .nextDueDate(next != null ? next.dueDate() : null)
                .nextDueAmount(next != null ? next.amount() : null)
                .cuotasActivas(buildCuotasActivas(debt))
                .vencimientos(vencimientos)
                .build();
    }

    private List<InstallmentPlanResponse> buildCuotasActivas(List<Transaction> debt) {
        // Solo cuotas (grupo no nulo). Cada grupo: cuota actual = la pendiente más temprana.
        Map<UUID, List<Transaction>> byGroup = groupBy(
                debt.stream().filter(t -> t.getInstallmentGroupId() != null).toList(),
                Transaction::getInstallmentGroupId);

        List<InstallmentPlanResponse> plans = new ArrayList<>();
        for (Map.Entry<UUID, List<Transaction>> e : byGroup.entrySet()) {
            Transaction current = e.getValue().stream()
                    .min(Comparator.comparing(Transaction::getInstallmentNumber))
                    .orElseThrow();
            plans.add(InstallmentPlanResponse.builder()
                    .groupId(e.getKey())
                    .description(baseDescription(current.getDescription()))
                    .cardName(cardName(current.getCard()))
                    .categoryIcon(current.getCategory() != null ? current.getCategory().getIcon() : null)
                    .categoryColor(current.getCategory() != null ? current.getCategory().getColor() : null)
                    .currentNumber(current.getInstallmentNumber())
                    .totalInstallments(current.getTotalInstallments())
                    .monthlyAmount(current.getAmount())
                    .ccy(current.getCcy())
                    .build());
        }
        plans.sort(Comparator.comparing(InstallmentPlanResponse::description));
        return plans;
    }

    // ── Matriz (Pivot Tree Grid) ───────────────────────────────────────────────

    public CardMatrixResponse matrix(UUID userId, int months) {
        YearMonth firstMonth = YearMonth.now();
        List<YearMonth> window = new ArrayList<>(months);
        List<String> headers = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth ym = firstMonth.plusMonths(i);
            window.add(ym);
            headers.add(ym.toString()); // "YYYY-MM"; el frontend lo formatea
        }

        List<Transaction> debt = transactionRepository.findCardTransactionsFromDate(userId, firstMonth.atDay(1));
        Map<UUID, List<Transaction>> byCard = groupBy(debt, t -> t.getCard().getId());

        List<CardMatrixCard> cards = new ArrayList<>();
        BigDecimal[] totals = zeros(months);

        for (Map.Entry<UUID, List<Transaction>> cardEntry : byCard.entrySet()) {
            List<Transaction> cardDebt = cardEntry.getValue();
            CreditCard card = cardDebt.get(0).getCard();

            // Agrupar por consumo: grupo de cuotas, o el id de la transacción si es pago único.
            Map<String, List<Transaction>> byConsumo = groupBy(cardDebt,
                    t -> t.getInstallmentGroupId() != null
                            ? "g:" + t.getInstallmentGroupId()
                            : "t:" + t.getId());

            List<CardMatrixConsumo> consumos = new ArrayList<>();
            BigDecimal[] subtotals = zeros(months);

            for (List<Transaction> rows : byConsumo.values()) {
                BigDecimal[] cells = new BigDecimal[months];
                Boolean[] paidByMonth = new Boolean[months];
                boolean hasCell = false;
                for (Transaction t : rows) {
                    int idx = window.indexOf(YearMonth.from(t.getDueDate()));
                    if (idx >= 0) {
                        cells[idx] = cells[idx] == null ? t.getAmount() : cells[idx].add(t.getAmount());
                        subtotals[idx] = subtotals[idx].add(t.getAmount());
                        totals[idx]    = totals[idx].add(t.getAmount());
                        paidByMonth[idx] = paidByMonth[idx] == null ? t.isPaid() : paidByMonth[idx] && t.isPaid();
                        hasCell = true;
                    }
                }
                if (!hasCell) continue;

                Transaction ref = rows.stream()
                        .min(Comparator.comparing(t -> t.getInstallmentNumber() == null ? 1 : t.getInstallmentNumber()))
                        .orElse(rows.get(0));
                String label = ref.isInstallment()
                        ? ref.getInstallmentNumber() + "/" + ref.getTotalInstallments()
                        : null;

                consumos.add(CardMatrixConsumo.builder()
                        .description(baseDescription(ref.getDescription()))
                        .installmentLabel(label)
                        .categoryIcon(ref.getCategory() != null ? ref.getCategory().getIcon() : null)
                        .categoryColor(ref.getCategory() != null ? ref.getCategory().getColor() : null)
                        .cellsByMonth(toList(cells))
                        .paidByMonth(toListBoolean(paidByMonth))
                        .build());
            }

            if (consumos.isEmpty()) continue;
            consumos.sort(Comparator.comparing(CardMatrixConsumo::description));

            cards.add(CardMatrixCard.builder()
                    .cardId(card.getId())
                    .cardName(cardName(card))
                    .accent(card.getAccent())
                    .consumos(consumos)
                    .subtotalsByMonth(toList(subtotals))
                    .build());
        }

        cards.sort(Comparator.comparing(CardMatrixCard::cardName));

        return CardMatrixResponse.builder()
                .months(headers)
                .cards(cards)
                .totalsByMonth(toList(totals))
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private <K> Map<K, List<Transaction>> groupBy(List<Transaction> txs, java.util.function.Function<Transaction, K> key) {
        Map<K, List<Transaction>> map = new LinkedHashMap<>();
        for (Transaction t : txs) {
            map.computeIfAbsent(key.apply(t), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private BigDecimal sum(List<Transaction> txs) {
        return txs.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal[] zeros(int n) {
        BigDecimal[] arr = new BigDecimal[n];
        for (int i = 0; i < n; i++) arr[i] = BigDecimal.ZERO;
        return arr;
    }

    private List<BigDecimal> toList(BigDecimal[] arr) {
        List<BigDecimal> list = new ArrayList<>(arr.length);
        for (BigDecimal v : arr) list.add(v);
        return list;
    }

    private List<Boolean> toListBoolean(Boolean[] arr) {
        List<Boolean> list = new ArrayList<>(arr.length);
        for (Boolean v : arr) list.add(v);
        return list;
    }

    private String cardName(CreditCard card) {
        return card.getBank() + " ····" + card.getLast4();
    }

    /** Quita el sufijo " — cuota k/N" de la descripción de una cuota. */
    private String baseDescription(String description) {
        int idx = description.indexOf(" — cuota ");
        return idx >= 0 ? description.substring(0, idx) : description;
    }
}
