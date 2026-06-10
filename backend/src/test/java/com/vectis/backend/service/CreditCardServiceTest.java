package com.vectis.backend.service;

import com.vectis.backend.domain.entity.CreditCard;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardRequest;
import com.vectis.backend.dto.CardResponse;
import com.vectis.backend.exception.CreditCardNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.CreditCardMapper;
import com.vectis.backend.repository.CreditCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditCardService")
class CreditCardServiceTest {

    @InjectMocks
    private CreditCardService creditCardService;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CreditCardMapper creditCardMapper;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        otherId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        otherUser = User.builder()
                .id(otherId)
                .email("other@vectis.com")
                .fullName("Other User")
                .passwordHash("hash")
                .build();
    }

    // ─── getCards ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCards devuelve solo las tarjetas del usuario")
    void getCards_returnsOnlyUserCards() {
        CreditCard card = buildCard(user);
        CardResponse response = buildResponse(card);

        given(creditCardRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)).willReturn(List.of(card));
        given(creditCardMapper.toResponse(card)).willReturn(response);

        List<CardResponse> result = creditCardService.getCards(userId);

        assertThat(result).hasSize(1);
        verify(creditCardRepository).findAllByUser_IdOrderByCreatedAtAsc(userId);
    }

    // ─── createCard ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createCard persiste con el usuario autenticado")
    void createCard_persistsWithAuthenticatedUser() {
        CardRequest request = buildRequest();
        CreditCard saved = buildCard(user);
        CardResponse response = buildResponse(saved);

        given(creditCardRepository.save(any(CreditCard.class))).willReturn(saved);
        given(creditCardMapper.toResponse(saved)).willReturn(response);

        CardResponse result = creditCardService.createCard(request, user);

        assertThat(result.bank()).isEqualTo("Galicia");
        verify(creditCardRepository).save(any(CreditCard.class));
    }

    // ─── updateCard ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCard de tarjeta inexistente lanza NOT_FOUND")
    void updateCard_notFound_throwsCreditCardNotFoundException() {
        UUID id = UUID.randomUUID();
        given(creditCardRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.updateCard(id, buildRequest(), user))
                .isInstanceOf(CreditCardNotFoundException.class);
    }

    @Test
    @DisplayName("updateCard de tarjeta de otro usuario lanza FORBIDDEN")
    void updateCard_otherUserCard_throwsForbidden() {
        UUID id = UUID.randomUUID();
        CreditCard otherCard = buildCard(otherUser);
        given(creditCardRepository.findById(id)).willReturn(Optional.of(otherCard));

        assertThatThrownBy(() -> creditCardService.updateCard(id, buildRequest(), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateCard propia actualiza todos los campos")
    void updateCard_ownCard_updatesAllFields() {
        UUID id = UUID.randomUUID();
        CreditCard card = buildCard(user);
        CardRequest request = new CardRequest(
                "Santander", "Mastercard", "9999", "USD",
                new BigDecimal("1000000.0000"), 20, 10, "#9ed1c5");
        CardResponse response = buildResponse(card);

        given(creditCardRepository.findById(id)).willReturn(Optional.of(card));
        given(creditCardRepository.save(card)).willReturn(card);
        given(creditCardMapper.toResponse(card)).willReturn(response);

        CardResponse result = creditCardService.updateCard(id, request, user);

        assertThat(result).isNotNull();
        verify(creditCardRepository).save(card);
    }

    // ─── deleteCard ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCard de tarjeta inexistente lanza NOT_FOUND")
    void deleteCard_notFound_throwsCreditCardNotFoundException() {
        UUID id = UUID.randomUUID();
        given(creditCardRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.deleteCard(id, user))
                .isInstanceOf(CreditCardNotFoundException.class);
    }

    @Test
    @DisplayName("deleteCard de tarjeta de otro usuario lanza FORBIDDEN")
    void deleteCard_otherUserCard_throwsForbidden() {
        UUID id = UUID.randomUUID();
        CreditCard otherCard = buildCard(otherUser);
        given(creditCardRepository.findById(id)).willReturn(Optional.of(otherCard));

        assertThatThrownBy(() -> creditCardService.deleteCard(id, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deleteCard propia elimina la tarjeta")
    void deleteCard_ownCard_deletesSuccessfully() {
        UUID id = UUID.randomUUID();
        CreditCard card = buildCard(user);
        given(creditCardRepository.findById(id)).willReturn(Optional.of(card));

        creditCardService.deleteCard(id, user);

        verify(creditCardRepository).delete(card);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CreditCard buildCard(User owner) {
        return CreditCard.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .bank("Galicia")
                .network("Visa")
                .last4("1234")
                .ccy("ARS")
                .creditLimit(new BigDecimal("500000.0000"))
                .closingDay(15)
                .dueDay(5)
                .accent("#52eacd")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private CardRequest buildRequest() {
        return new CardRequest(
                "Galicia", "Visa", "1234", "ARS",
                new BigDecimal("500000.0000"), 15, 5, "#52eacd");
    }

    private CardResponse buildResponse(CreditCard c) {
        return new CardResponse(
                c.getId(), c.getBank(), c.getNetwork(), c.getLast4(), c.getCcy(),
                c.getCreditLimit(), c.getClosingDay(), c.getDueDay(), c.getAccent(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
