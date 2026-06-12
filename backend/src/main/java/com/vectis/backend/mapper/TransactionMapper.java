package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.dto.MovementResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public MovementResponse toResponse(Transaction t) {
        return MovementResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .description(t.getDescription())
                .amount(t.getAmount())
                .ccy(t.getCcy())
                .categoryId(t.getCategory() != null ? t.getCategory().getId() : null)
                .categoryName(t.getCategory() != null ? t.getCategory().getName() : null)
                .categoryIcon(t.getCategory() != null ? t.getCategory().getIcon() : null)
                .categoryColor(t.getCategory() != null ? t.getCategory().getColor() : null)
                .accountId(t.getAccount() != null ? t.getAccount().getId() : null)
                .accountName(t.getAccount() != null ? t.getAccount().getName() : null)
                .cardId(t.getCard() != null ? t.getCard().getId() : null)
                .cardName(t.getCard() != null ? t.getCard().getBank() + " ····" + t.getCard().getLast4() : null)
                .transactionDate(t.getTransactionDate())
                .dueDate(t.getDueDate())
                .installment(t.isInstallment())
                .installmentNumber(t.getInstallmentNumber())
                .totalInstallments(t.getTotalInstallments())
                .installmentGroupId(t.getInstallmentGroupId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
