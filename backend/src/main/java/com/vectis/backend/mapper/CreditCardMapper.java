package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.dto.CardResponse;
import org.springframework.stereotype.Component;

@Component
public class CreditCardMapper {

    public CardResponse toResponse(CreditCard card) {
        return CardResponse.builder()
                .id(card.getId())
                .bank(card.getBank())
                .network(card.getNetwork())
                .last4(card.getLast4())
                .ccy(card.getCcy())
                .creditLimit(card.getCreditLimit())
                .closingDay(card.getClosingDay())
                .dueDay(card.getDueDay())
                .accent(card.getAccent())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }
}
