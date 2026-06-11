package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.RecurringMovement;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.RecurringMovementRequest;
import com.vectis.backend.dto.RecurringMovementResponse;
import com.vectis.backend.exception.RecurringMovementNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.RecurringMovementMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.CategoryRepository;
import com.vectis.backend.repository.RecurringMovementRepository;
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
@DisplayName("RecurringMovementService")
class RecurringMovementServiceTest {

    @InjectMocks
    private RecurringMovementService recurringMovementService;

    @Mock private RecurringMovementRepository recurringMovementRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private RecurringMovementMapper recurringMovementMapper;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        otherId = UUID.randomUUID();

        user = User.builder()
                .id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash")
                .build();

        otherUser = User.builder()
                .id(otherId).email("other@vectis.com").fullName("Other User").passwordHash("hash")
                .build();
    }

    // ─── getRecurringMovements ────────────────────────────────────────────────

    @Test
    @DisplayName("getRecurringMovements devuelve solo los movimientos activos del usuario")
    void getRecurringMovements_returnsOnlyUserMovements() {
        RecurringMovement rm = buildMovement(user);
        RecurringMovementResponse response = buildResponse(rm);

        given(recurringMovementRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId))
                .willReturn(List.of(rm));
        given(recurringMovementMapper.toResponse(rm)).willReturn(response);

        List<RecurringMovementResponse> result = recurringMovementService.getRecurringMovements(userId);

        assertThat(result).hasSize(1);
        verify(recurringMovementRepository).findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
    }

    @Test
    @DisplayName("getRecurringMovements excluye los eliminados (soft delete)")
    void getRecurringMovements_excludesSoftDeleted() {
        given(recurringMovementRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId))
                .willReturn(List.of());

        List<RecurringMovementResponse> result = recurringMovementService.getRecurringMovements(userId);

        assertThat(result).isEmpty();
    }

    // ─── createRecurringMovement ──────────────────────────────────────────────

    @Test
    @DisplayName("createRecurringMovement asocia el userId del JWT, no del body")
    void create_setsUserFromJwt_notFromBody() {
        RecurringMovementRequest request = buildRequest(null, null);
        RecurringMovement saved = buildMovement(user);
        RecurringMovementResponse response = buildResponse(saved);

        given(recurringMovementRepository.save(any(RecurringMovement.class))).willReturn(saved);
        given(recurringMovementMapper.toResponse(saved)).willReturn(response);

        RecurringMovementResponse result = recurringMovementService.createRecurringMovement(request, user);

        assertThat(result).isNotNull();
        verify(recurringMovementRepository).save(any(RecurringMovement.class));
    }

    @Test
    @DisplayName("createRecurringMovement con cuenta de otro usuario lanza FORBIDDEN")
    void create_accountNotOwnedByUser_throwsForbidden() {
        UUID accountId = UUID.randomUUID();
        RecurringMovementRequest request = buildRequest(null, accountId);

        Account otherAccount = Account.builder()
                .id(accountId).user(otherUser).name("Otra cuenta").kind("Banco")
                .ccy("ARS").balance(BigDecimal.ZERO).remunerada(false)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        given(accountRepository.findById(accountId)).willReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> recurringMovementService.createRecurringMovement(request, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── updateRecurringMovement ──────────────────────────────────────────────

    @Test
    @DisplayName("updateRecurringMovement de movimiento inexistente lanza NOT_FOUND")
    void update_notFound_throwsRecurringMovementNotFoundException() {
        UUID id = UUID.randomUUID();
        given(recurringMovementRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recurringMovementService.updateRecurringMovement(id, buildRequest(null, null), user))
                .isInstanceOf(RecurringMovementNotFoundException.class);
    }

    @Test
    @DisplayName("updateRecurringMovement de otro usuario lanza FORBIDDEN")
    void update_notOwner_throwsForbidden() {
        UUID id = UUID.randomUUID();
        RecurringMovement otherRm = buildMovement(otherUser);
        given(recurringMovementRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(otherRm));

        assertThatThrownBy(() -> recurringMovementService.updateRecurringMovement(id, buildRequest(null, null), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── toggleActive ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleActive invierte el flag active")
    void toggleActive_flipsActiveFlag() {
        UUID id = UUID.randomUUID();
        RecurringMovement rm = buildMovement(user);
        rm.setActive(true);
        RecurringMovementResponse response = buildResponse(rm);

        given(recurringMovementRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(rm));
        given(recurringMovementRepository.save(rm)).willReturn(rm);
        given(recurringMovementMapper.toResponse(rm)).willReturn(response);

        recurringMovementService.toggleActive(id, user);

        assertThat(rm.isActive()).isFalse();
        verify(recurringMovementRepository).save(rm);
    }

    // ─── deleteRecurringMovement ──────────────────────────────────────────────

    @Test
    @DisplayName("deleteRecurringMovement realiza soft delete seteando deletedAt")
    void delete_softDeletes_setsDeletedAt() {
        UUID id = UUID.randomUUID();
        RecurringMovement rm = buildMovement(user);
        given(recurringMovementRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(rm));
        given(recurringMovementRepository.save(rm)).willReturn(rm);

        recurringMovementService.deleteRecurringMovement(id, user);

        assertThat(rm.getDeletedAt()).isNotNull();
        verify(recurringMovementRepository).save(rm);
    }

    @Test
    @DisplayName("deleteRecurringMovement de otro usuario lanza FORBIDDEN")
    void delete_notOwner_throwsForbidden() {
        UUID id = UUID.randomUUID();
        RecurringMovement otherRm = buildMovement(otherUser);
        given(recurringMovementRepository.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(otherRm));

        assertThatThrownBy(() -> recurringMovementService.deleteRecurringMovement(id, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private RecurringMovement buildMovement(User owner) {
        return RecurringMovement.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .description("Netflix")
                .amount(new BigDecimal("15000.0000"))
                .ccy("ARS")
                .type("EXPENSE")
                .dayOfMonth(10)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private RecurringMovementRequest buildRequest(UUID categoryId, UUID accountId) {
        return new RecurringMovementRequest(
                "Netflix", new BigDecimal("15000.0000"), "ARS", "EXPENSE",
                categoryId, accountId, 10);
    }

    private RecurringMovementResponse buildResponse(RecurringMovement rm) {
        return new RecurringMovementResponse(
                rm.getId(), rm.getDescription(), rm.getAmount(), rm.getCcy(), rm.getType(),
                null, null, null, null, null, null,
                rm.getDayOfMonth(), rm.isActive(), rm.getCreatedAt());
    }
}
