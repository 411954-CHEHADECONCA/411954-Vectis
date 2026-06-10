package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardRequest;
import com.vectis.backend.dto.CardResponse;
import com.vectis.backend.exception.CreditCardNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.CreditCardMapper;
import com.vectis.backend.repository.CreditCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardMapper     creditCardMapper;

    @Transactional(readOnly = true)
    public List<CardResponse> getCards(UUID userId) {
        return creditCardRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)
                .stream()
                .map(creditCardMapper::toResponse)
                .toList();
    }

    public CardResponse createCard(CardRequest request, User user) {
        CreditCard card = CreditCard.builder()
                .user(user)
                .bank(request.bank())
                .network(request.network())
                .last4(request.last4())
                .ccy(request.ccy())
                .creditLimit(request.creditLimit())
                .closingDay(request.closingDay())
                .dueDay(request.dueDay())
                .accent(request.accent())
                .build();
        return creditCardMapper.toResponse(creditCardRepository.save(card));
    }

    public CardResponse updateCard(UUID id, CardRequest request, User user) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new CreditCardNotFoundException(id));

        if (!card.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para modificar esta tarjeta", HttpStatus.FORBIDDEN);
        }

        card.setBank(request.bank());
        card.setNetwork(request.network());
        card.setLast4(request.last4());
        card.setCcy(request.ccy());
        card.setCreditLimit(request.creditLimit());
        card.setClosingDay(request.closingDay());
        card.setDueDay(request.dueDay());
        card.setAccent(request.accent());

        return creditCardMapper.toResponse(creditCardRepository.save(card));
    }

    public void deleteCard(UUID id, User user) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new CreditCardNotFoundException(id));

        if (!card.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para eliminar esta tarjeta", HttpStatus.FORBIDDEN);
        }

        creditCardRepository.delete(card);
    }
}
