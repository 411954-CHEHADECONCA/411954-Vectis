package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.dto.RecurringMovementResponse;
import org.springframework.stereotype.Component;

@Component
public class RecurringMovementMapper {

    public RecurringMovementResponse toResponse(RecurringMovement rm) {
        return RecurringMovementResponse.builder()
                .id(rm.getId())
                .description(rm.getDescription())
                .amount(rm.getAmount())
                .ccy(rm.getCcy())
                .type(rm.getType())
                .categoryId(rm.getCategory() != null ? rm.getCategory().getId() : null)
                .categoryName(rm.getCategory() != null ? rm.getCategory().getName() : null)
                .categoryIcon(rm.getCategory() != null ? rm.getCategory().getIcon() : null)
                .categoryColor(rm.getCategory() != null ? rm.getCategory().getColor() : null)
                .accountId(rm.getAccount() != null ? rm.getAccount().getId() : null)
                .accountName(rm.getAccount() != null ? rm.getAccount().getName() : null)
                .cardId(rm.getCard() != null ? rm.getCard().getId() : null)
                .cardName(rm.getCard() != null ? rm.getCard().getBank() + " ····" + rm.getCard().getLast4() : null)
                .dayOfMonth(rm.getDayOfMonth())
                .active(rm.isActive())
                .createdAt(rm.getCreatedAt())
                .build();
    }
}
