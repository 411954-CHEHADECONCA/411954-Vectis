package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.InvestmentMovementNotFoundException;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentValuationNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.InvestmentMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.InvestmentMovementRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import com.vectis.backend.domain.entity.InvestmentValuation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentService")
class InvestmentServiceTest {

    @InjectMocks
    private InvestmentService investmentService;

    @Mock private InvestmentRepository           investmentRepository;
    @Mock private InvestmentMovementRepository   movementRepository;
    @Mock private InvestmentValuationRepository  valuationRepository;
    @Mock private AccountRepository              accountRepository;
    @Mock private InvestmentMapper               investmentMapper;
    @Mock private FciValuationSyncService        fciValuationSyncService;

    private User    user;
    private UUID    userId;
    private Account account;
    private UUID    accountId;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        account = Account.builder()
                .id(accountId)
                .user(user)
                .name("Cuenta Galicia")
                .kind("Banco")
                .ccy("ARS")
                .balance(new BigDecimal("100000.0000"))
                .remunerada(false)
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── getInvestments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvestments retorna lista mapeada del usuario")
    void getInvestments_returnsAllForUser() {
        InvestmentAsset asset   = buildAsset(UUID.randomUUID(), null);
        InvestmentResponse resp = buildResponse(asset.getId(), null, null);

        given(investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of(asset));
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        List<InvestmentResponse> result = investmentService.getInvestments(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(asset.getId());
        verify(investmentMapper, times(1)).toResponse(asset);
    }

    @Test
    @DisplayName("getInvestments retorna lista vacía sin excepción cuando no hay activos")
    void getInvestments_returnsEmptyList_whenNoAssets() {
        given(investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of());

        List<InvestmentResponse> result = investmentService.getInvestments(userId);

        assertThat(result).isEmpty();
        verify(investmentMapper, never()).toResponse(any());
    }

    // ─── createInvestment ─────────────────────────────────────────────────────

    @Test
    @DisplayName("createInvestment sin cuenta guarda correctamente y response.accountId es null")
    void createInvestment_withoutAccount_savesCorrectly() {
        InvestmentRequest req  = buildRequest(null);
        InvestmentAsset   saved = buildAsset(UUID.randomUUID(), null);
        InvestmentResponse resp = buildResponse(saved.getId(), null, null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willReturn(saved);
        given(investmentMapper.toResponse(saved)).willReturn(resp);

        InvestmentResponse result = investmentService.createInvestment(req, user);

        verify(investmentRepository).save(any(InvestmentAsset.class));
        assertThat(result.accountId()).isNull();
    }

    @Test
    @DisplayName("createInvestment con accountId válido resuelve la cuenta correctamente")
    void createInvestment_withAccount_resolvesAccountCorrectly() {
        InvestmentRequest req   = buildRequest(accountId);
        InvestmentAsset   saved  = buildAsset(UUID.randomUUID(), account);
        InvestmentResponse resp  = buildResponse(saved.getId(), accountId, "Cuenta Galicia");

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(investmentRepository.save(any(InvestmentAsset.class))).willReturn(saved);
        given(investmentMapper.toResponse(saved)).willReturn(resp);

        InvestmentResponse result = investmentService.createInvestment(req, user);

        verify(accountRepository).findByIdAndUser_Id(accountId, userId);
        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getAccount()).isEqualTo(account);
        assertThat(result.accountId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("createInvestment setea autoTrack y externalId cuando se proveen")
    void createInvestment_setsAutoTrackAndExternalId_whenProvided() {
        InvestmentRequest req = new InvestmentRequest(
                "Cocos - Clase A", InvestmentAssetType.FCI_CUOTAPARTES, "ARS",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                true,
                "Cocos Capital - Clase A");

        InvestmentAsset saved = InvestmentAsset.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Cocos - Clase A")
                .type(InvestmentAssetType.FCI_CUOTAPARTES)
                .currency("ARS")
                .principal(BigDecimal.ZERO)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .autoTrack(true)
                .externalId("Cocos Capital - Clase A")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        InvestmentResponse resp = InvestmentResponse.builder()
                .id(saved.getId())
                .name("Cocos - Clase A")
                .type("FCI_CUOTAPARTES")
                .currency("ARS")
                .principal(BigDecimal.ZERO)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(true)
                .externalId("Cocos Capital - Clase A")
                .movements(List.of())
                .valuations(List.of())
                .build();

        given(investmentRepository.save(any(InvestmentAsset.class))).willReturn(saved);
        given(investmentMapper.toResponse(saved)).willReturn(resp);

        InvestmentResponse result = investmentService.createInvestment(req, user);

        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        InvestmentAsset capturedAsset = captor.getValue();
        assertThat(capturedAsset.isAutoTrack()).isTrue();
        assertThat(capturedAsset.getExternalId()).isEqualTo("Cocos Capital - Clase A");
        assertThat(result.autoTrack()).isTrue();
        assertThat(result.externalId()).isEqualTo("Cocos Capital - Clase A");
    }

    @Test
    @DisplayName("createInvestment con cuenta de otro usuario lanza VectisException 403")
    void createInvestment_withAccountOfAnotherUser_throwsForbidden() {
        InvestmentRequest req = buildRequest(accountId);
        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(investmentRepository, never()).save(any());
    }

    // ─── updateInvestment ─────────────────────────────────────────────────────

    @Test
    @DisplayName("updateInvestment PLAZO_FIJO actualiza todos los campos incluyendo el principal")
    void updateInvestment_updatesAllFields() {
        UUID            assetId = UUID.randomUUID();
        InvestmentAsset asset   = InvestmentAsset.builder()
                .id(assetId).user(user).name("PF Original")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .maturityDate(LocalDate.of(2026, 6, 1))
                .tna(new BigDecimal("30.0000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        InvestmentRequest req = new InvestmentRequest(
                "PF Actualizado", InvestmentAssetType.PLAZO_FIJO, "USD",
                new BigDecimal("2000000.00"),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2027, 2, 1),
                new BigDecimal("55.00"),
                null,
                false,
                null);
        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.updateInvestment(assetId, req, user);

        assertThat(asset.getName()).isEqualTo("PF Actualizado");
        assertThat(asset.getType()).isEqualTo(InvestmentAssetType.PLAZO_FIJO);
        assertThat(asset.getCurrency()).isEqualTo("USD");
        assertThat(asset.getPrincipal()).isEqualByComparingTo("2000000.00");
        assertThat(asset.getTna()).isEqualByComparingTo("55.00");
        assertThat(asset.getAccount()).isNull();
        verify(investmentRepository).save(asset);
    }

    @Test
    @DisplayName("updateInvestment LETRA/BONO/ON no sobreescribe el principal derivado de movimientos")
    void updateInvestment_letraBonoOn_doesNotOverwritePrincipal() {
        UUID            assetId   = UUID.randomUUID();
        InvestmentAsset letraAsset = buildAsset(assetId, null); // LETRA, principal=$1.000.000
        InvestmentRequest req = new InvestmentRequest(
                "LETRA Renombrada", InvestmentAssetType.LETRA, "USD",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2027, 1, 15),
                new BigDecimal("55.00"),
                null,
                false,
                null);
        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(letraAsset));
        given(investmentRepository.save(letraAsset)).willReturn(letraAsset);
        given(investmentMapper.toResponse(letraAsset)).willReturn(resp);

        investmentService.updateInvestment(assetId, req, user);

        assertThat(letraAsset.getPrincipal()).isEqualByComparingTo("1000000.0000");
    }

    @Test
    @DisplayName("updateInvestment FCI no sobreescribe el principal con el del request")
    void updateInvestment_fci_doesNotOverwritePrincipal() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset fciAsset = buildFciAsset(assetId, new BigDecimal("600000.0000"));
        InvestmentRequest req = new InvestmentRequest(
                "FCI Actualizado", InvestmentAssetType.FCI, "ARS",
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("47.00"),
                null,
                false,
                null);
        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(fciAsset));
        given(investmentRepository.save(fciAsset)).willReturn(fciAsset);
        given(investmentMapper.toResponse(fciAsset)).willReturn(resp);

        investmentService.updateInvestment(assetId, req, user);

        // Principal must remain the balance from movements, not the request value (0)
        assertThat(fciAsset.getPrincipal()).isEqualByComparingTo("600000.0000");
    }

    @Test
    @DisplayName("updateInvestment lanza InvestmentNotFoundException cuando el activo no existe")
    void updateInvestment_throwsNotFound_whenMissing() {
        UUID missingId = UUID.randomUUID();
        given(investmentRepository.findByIdAndUser_Id(missingId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.updateInvestment(missingId, buildRequest(null), user))
                .isInstanceOf(InvestmentNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInvestment lanza InvestmentNotFoundException cuando el activo pertenece a otro usuario")
    void updateInvestment_throwsNotFound_whenNotOwner() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, buildRequest(null), user))
                .isInstanceOf(InvestmentNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── deleteInvestment ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteInvestment elimina el activo por ID")
    void deleteInvestment_deletesById() {
        UUID            assetId = UUID.randomUUID();
        InvestmentAsset asset   = buildAsset(assetId, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        investmentService.deleteInvestment(assetId, user);

        verify(investmentRepository).delete(asset);
    }

    @Test
    @DisplayName("deleteInvestment lanza InvestmentNotFoundException cuando el activo no pertenece al usuario")
    void deleteInvestment_throwsNotFound_whenNotOwner() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.deleteInvestment(assetId, user))
                .isInstanceOf(InvestmentNotFoundException.class);

        verify(investmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteInvestment lanza InvestmentNotFoundException cuando el activo no existe")
    void deleteInvestment_throwsNotFound_whenMissing() {
        UUID missingId = UUID.randomUUID();
        given(investmentRepository.findByIdAndUser_Id(missingId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.deleteInvestment(missingId, user))
                .isInstanceOf(InvestmentNotFoundException.class);

        verify(investmentRepository, never()).delete(any());
    }

    // ─── addMovement ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMovement SUSCRIPCION agrega el movimiento y recalcula el principal")
    void addMovement_suscripcion_updatesBalance() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("500000.00"), null);
        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getMovements()).hasSize(1);
        assertThat(asset.getMovements().get(0).getType()).isEqualTo(InvestmentMovementType.SUSCRIPCION);
        assertThat(asset.getPrincipal()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("addMovement RESCATE que supera el saldo lanza VectisException 422")
    void addMovement_rescate_exceedsBalance_throws422() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("100000.00"));
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 1), InvestmentMovementType.RESCATE, new BigDecimal("200000.00"), null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMovement lanza InvestmentNotFoundException cuando el activo no pertenece al usuario")
    void addMovement_notOwner_throwsNotFound() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("100000.00"), null);

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(InvestmentNotFoundException.class);
    }

    // ─── deleteMovement ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteMovement elimina el movimiento y recalcula el principal")
    void deleteMovement_removesMovementAndRecalculates() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00"))
                .build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));
        asset.getMovements().add(movement);

        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.deleteMovement(assetId, movId, user);

        assertThat(asset.getMovements()).isEmpty();
        assertThat(asset.getPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("deleteMovement lanza InvestmentMovementNotFoundException cuando el movimiento no existe")
    void deleteMovement_notFound_throws404() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.deleteMovement(assetId, movId, user))
                .isInstanceOf(InvestmentMovementNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── updateMovement (FCI) ─────────────────────────────────────────────────

    @Test
    @DisplayName("updateMovement fija el interestOverride de un tramo SUSCRIPCION sin tocar el amount")
    void updateMovement_setsInterestOverride_onSuscripcion() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId)
                .investmentAsset(null)
                .movementDate(LocalDate.of(2026, 6, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("1000000.00"));
        asset.getMovements().add(movement);

        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("12500.00"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.updateMovement(assetId, movId, req, user);

        assertThat(movement.getInterestOverride()).isEqualByComparingTo("12500.00");
        assertThat(movement.getAmount()).isEqualByComparingTo("1000000.00");
        // override no capitaliza: el principal se mantiene en el monto de la suscripción
        assertThat(asset.getPrincipal()).isEqualByComparingTo("1000000.00");
    }

    @Test
    @DisplayName("updateMovement ajusta el amount de un tramo REVALUO y recalcula el principal (capitaliza)")
    void updateMovement_updatesRevaluoAmount_recapitalizes() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .movementDate(LocalDate.of(2026, 5, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .build();
        InvestmentMovement revaluo = InvestmentMovement.builder()
                .id(movId)
                .movementDate(LocalDate.of(2026, 6, 1))
                .type(InvestmentMovementType.REVALUO)
                .amount(new BigDecimal("30000.00"))
                .build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("1030000.00"));
        asset.getMovements().add(suscripcion);
        asset.getMovements().add(revaluo);

        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(new BigDecimal("20000.00"), null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(revaluo));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.updateMovement(assetId, movId, req, user);

        assertThat(revaluo.getAmount()).isEqualByComparingTo("20000.00");
        // REVALUO capitaliza: principal = 1.000.000 + 20.000
        assertThat(asset.getPrincipal()).isEqualByComparingTo("1020000.00");
    }

    @Test
    @DisplayName("updateMovement con interestOverride null restaura el cálculo por TNA")
    void updateMovement_nullOverride_clearsIt() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId)
                .movementDate(LocalDate.of(2026, 6, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .interestOverride(new BigDecimal("9999.00"))
                .build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("1000000.00"));
        asset.getMovements().add(movement);

        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.updateMovement(assetId, movId, req, user);

        assertThat(movement.getInterestOverride()).isNull();
    }

    @Test
    @DisplayName("updateMovement rechaza activos que no son FCI con 409 (aislamiento de otras inversiones)")
    void updateMovement_nonFci_throws409() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("1000000.00"));

        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("100.00"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.updateMovement(assetId, movId, req, user))
                .isInstanceOf(VectisException.class)
                .hasMessageContaining("Cuenta Remunerada");

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMovement lanza InvestmentMovementNotFoundException cuando el movimiento no existe")
    void updateMovement_movementNotFound_throws404() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);

        InvestmentMovementUpdateRequest req =
                new InvestmentMovementUpdateRequest(null, new BigDecimal("100.00"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.updateMovement(assetId, movId, req, user))
                .isInstanceOf(InvestmentMovementNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── Movimientos backdateados: permitidos (el cálculo ordena por fecha) ───

    @Test
    @DisplayName("addMovement permite un movimiento con fecha anterior a otro ya registrado")
    void addMovement_backdatedMovement_isAllowed() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));

        // Pre-existing movement on Jan 1 2026
        InvestmentMovement existing = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00"))
                .build();
        asset.getMovements().add(existing);

        // Request with a date BEFORE the existing movement (Dec 1 2025) — debe permitirse
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2025, 12, 1),
                InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("100000.00"),
                null);

        InvestmentResponse resp = buildResponse(assetId, null, null);
        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId))
                .willReturn(Optional.of(asset));
        given(investmentRepository.save(any(InvestmentAsset.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        InvestmentResponse result = investmentService.addMovement(assetId, req, user);

        assertThat(result).isNotNull();
        assertThat(asset.getMovements()).hasSize(2);
        verify(investmentRepository).save(asset);
    }

    // ─── FIX 2: validateValuationDateUniqueness ───────────────────────────────

    @Test
    @DisplayName("addValuation rechaza valuación en fecha ya registrada")
    void addValuation_duplicateDate_throwsConflict() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);

        // Pre-existing valuation on Mar 1 2026
        InvestmentValuation existingValuation = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 3, 1))
                .pricePerUnit(new BigDecimal("1200.0000"))
                .build();
        asset.getValuations().add(existingValuation);

        // Request for the same date
        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 3, 1),
                new BigDecimal("1300.00"));

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId))
                .willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addValuation(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    // ─── FIX 3: validateRescate extended to LETRA/BONO/ON ────────────────────

    @Test
    @DisplayName("addMovement LETRA rechaza RESCATE que excede nominales disponibles")
    void addMovement_rescateExceedsNominalesForLetra_throwsException() {
        UUID assetId = UUID.randomUUID();
        // LETRA asset with a SUSCRIPCION of 500 nominales
        InvestmentAsset asset = buildAsset(assetId, null); // type=LETRA, principal=1_000_000

        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 15))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .units(new BigDecimal("500.000000"))
                .build();
        asset.getMovements().add(suscripcion);

        // Rescate tries to return 600 nominales — more than the 500 held
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 8, 31),
                InvestmentMovementType.RESCATE,
                new BigDecimal("500000.00"),
                new BigDecimal("600.000000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId))
                .willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private InvestmentRequest buildRequest(UUID accountId) {
        return new InvestmentRequest(
                "LECAP S31G5", InvestmentAssetType.LETRA, "ARS",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 8, 31),
                new BigDecimal("65.00"),
                accountId,
                false,
                null);
    }

    private InvestmentAsset buildAsset(UUID id, Account account) {
        return InvestmentAsset.builder()
                .id(id)
                .user(user)
                .account(account)
                .name("LECAP S31G5")
                .type(InvestmentAssetType.LETRA)
                .currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .maturityDate(LocalDate.of(2026, 8, 31))
                .tna(new BigDecimal("65.0000"))
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private InvestmentAsset buildFciAsset(UUID id, BigDecimal balance) {
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(id)
                .user(user)
                .name("FCI Ahorro")
                .type(InvestmentAssetType.FCI)
                .currency("ARS")
                .principal(balance)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("47.0000"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        // Ensure mutable movements list
        asset.getMovements().clear();
        return asset;
    }

    private InvestmentResponse buildResponse(UUID id, UUID accountId, String accountName) {
        return InvestmentResponse.builder()
                .id(id)
                .name("LECAP S31G5")
                .type("LETRA")
                .currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .maturityDate(LocalDate.of(2026, 8, 31))
                .tna(new BigDecimal("65.0000"))
                .accountId(accountId)
                .accountName(accountName)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(false)
                .externalId(null)
                .movements(List.of())
                .valuations(List.of())
                .build();
    }

    private InvestmentAsset buildFciCuotapartesAsset(UUID id, BigDecimal balance) {
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(id)
                .user(user)
                .name("FCI Cuotapartes Test")
                .type(InvestmentAssetType.FCI_CUOTAPARTES)
                .currency("ARS")
                .principal(balance)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("0.0000"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        asset.getMovements().clear();
        asset.getValuations().clear();
        return asset;
    }

    private InvestmentValuation buildValuation(UUID valId, InvestmentAsset asset) {
        return InvestmentValuation.builder()
                .id(valId)
                .investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 6, 1))
                .pricePerUnit(new BigDecimal("1500.0000"))
                .build();
    }

    private InvestmentResponse buildFciCuotapartesResponse(UUID id) {
        return InvestmentResponse.builder()
                .id(id)
                .name("FCI Cuotapartes Test")
                .type("FCI_CUOTAPARTES")
                .currency("ARS")
                .principal(BigDecimal.ZERO)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .maturityDate(null)
                .tna(new BigDecimal("0.0000"))
                .accountId(null)
                .accountName(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .autoTrack(false)
                .externalId(null)
                .movements(List.of())
                .valuations(List.of())
                .build();
    }

    // ─── addValuation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addValuation persiste la valuación y retorna el activo actualizado")
    void addValuation_persistsAndReturnsUpdatedAsset() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1), new BigDecimal("1500.00"));
        InvestmentResponse resp = buildFciCuotapartesResponse(assetId);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        InvestmentResponse result = investmentService.addValuation(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("1500.00");
        assertThat(result).isNotNull();
        verify(investmentRepository).save(asset);
    }

    @Test
    @DisplayName("addValuation cuando el activo no pertenece al usuario lanza InvestmentNotFoundException")
    void addValuation_notOwner_throwsNotFound() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1), new BigDecimal("1500.00"));

        assertThatThrownBy(() -> investmentService.addValuation(assetId, req, user))
                .isInstanceOf(InvestmentNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── updateValuation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateValuation actualiza la fecha y el precio de la valuación")
    void updateValuation_updatesDateAndPrice() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset     asset     = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentValuation valuation = buildValuation(valId, asset);
        asset.getValuations().add(valuation);

        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("1800.00"));
        InvestmentResponse resp = buildFciCuotapartesResponse(assetId);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(valuationRepository.findByIdAndInvestmentAsset_Id(valId, assetId)).willReturn(Optional.of(valuation));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.updateValuation(assetId, valId, req, user);

        assertThat(valuation.getValuationDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(valuation.getPricePerUnit()).isEqualByComparingTo("1800.00");
        assertThat(valuation.getSource()).isEqualTo("MANUAL");
        verify(investmentRepository).save(asset);
    }

    @Test
    @DisplayName("updateValuation cuando la valuación no existe lanza InvestmentValuationNotFoundException")
    void updateValuation_notFound_throws404() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(valuationRepository.findByIdAndInvestmentAsset_Id(valId, assetId)).willReturn(Optional.empty());

        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("1800.00"));

        assertThatThrownBy(() -> investmentService.updateValuation(assetId, valId, req, user))
                .isInstanceOf(InvestmentValuationNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── deleteValuation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteValuation elimina la valuación y retorna el activo actualizado")
    void deleteValuation_removesAndReturnsUpdatedAsset() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset     asset     = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentValuation valuation = buildValuation(valId, asset);
        asset.getValuations().add(valuation);

        InvestmentResponse resp = buildFciCuotapartesResponse(assetId);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(valuationRepository.findByIdAndInvestmentAsset_Id(valId, assetId)).willReturn(Optional.of(valuation));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.deleteValuation(assetId, valId, user);

        assertThat(asset.getValuations()).isEmpty();
        verify(investmentRepository).save(asset);
    }

    @Test
    @DisplayName("deleteValuation cuando la valuación no existe lanza InvestmentValuationNotFoundException")
    void deleteValuation_notFound_throws404() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(valuationRepository.findByIdAndInvestmentAsset_Id(valId, assetId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.deleteValuation(assetId, valId, user))
                .isInstanceOf(InvestmentValuationNotFoundException.class);

        verify(investmentRepository, never()).save(any());
    }

    // ─── FCI_CUOTAPARTES rescate units validation ─────────────────────────────

    @Test
    @DisplayName("addMovement FCI_CUOTAPARTES rescate con units > cuotapartes disponibles lanza VectisException 422")
    void addMovement_fciCuotapartes_rescateExceedsUnits_throws422() {
        UUID assetId = UUID.randomUUID();

        // Build asset with one suscripcion of 100 units / $150000
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("150000.00"));
        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("150000.00"))
                .units(new BigDecimal("100.000000"))
                .build();
        asset.getMovements().add(suscripcion);

        // Rescate tries to redeem 200 units — more than the 100 held
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 1),
                InvestmentMovementType.RESCATE,
                new BigDecimal("100000.00"),
                new BigDecimal("200.000000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
    }

    // ─── REVALUO recalculatePrincipal ─────────────────────────────────────────

    @Test
    @DisplayName("addMovement REVALUO FCI suma al principal (NAV confirmado)")
    void addMovement_revaluo_fci_updatesBalance() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        // Pre-existing SUSCRIPCION movement so recalculatePrincipal picks it up
        InvestmentMovement existing = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00"))
                .build();
        asset.getMovements().add(existing);

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 4, 1), InvestmentMovementType.REVALUO, new BigDecimal("15000.00"), null);
        InvestmentResponse resp = buildResponse(assetId, null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.addMovement(assetId, req, user);

        // After: 2 movements (suscripcion + revaluo)
        assertThat(asset.getMovements()).hasSize(2);
        assertThat(asset.getMovements().get(1).getType()).isEqualTo(InvestmentMovementType.REVALUO);
        // SUSCRIPCION 500k + REVALUO 15k = 515k
        assertThat(asset.getPrincipal()).isEqualByComparingTo("515000.00");
    }

    @Test
    @DisplayName("addMovement REVALUO FCI_CUOTAPARTES NO modifica el principal (costo base intacto)")
    void addMovement_revaluo_fciCuotapartes_doesNotChangePrincipal() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("200000.00"));
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 4, 1), InvestmentMovementType.REVALUO, new BigDecimal("15000.00"), null);
        InvestmentResponse resp = buildFciCuotapartesResponse(assetId);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(resp);

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getMovements()).hasSize(1);
        // REVALUO en FCI_CP no aporta al principal (costo base no cambia)
        assertThat(asset.getPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── Backfill histórico de valuaciones al crear ──────────────────────────

    @Test
    @DisplayName("createInvestment dispara el backfill histórico para FCI_CUOTAPARTES con seguimiento automático")
    void createInvestment_triggersBackfill_forAutoTrackedFci() {
        InvestmentRequest req = new InvestmentRequest(
                "Alpha Pesos - Clase A", InvestmentAssetType.FCI_CUOTAPARTES, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 10), null, BigDecimal.ZERO,
                null, true, "Alpha Pesos - Clase A");

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildFciCuotapartesResponse(UUID.randomUUID()));
        given(fciValuationSyncService.backfillValuations(any(InvestmentAsset.class))).willReturn(120);

        investmentService.createInvestment(req, user);

        verify(fciValuationSyncService).backfillValuations(any(InvestmentAsset.class));
    }

    @Test
    @DisplayName("createInvestment NO dispara el backfill para un Plazo Fijo")
    void createInvestment_noBackfill_forPlazoFijo() {
        InvestmentRequest req = new InvestmentRequest(
                "PF 30 días", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("500000.00"), LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                null, false, null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildResponse(UUID.randomUUID(), null, null));

        investmentService.createInvestment(req, user);

        verify(fciValuationSyncService, never()).backfillValuations(any(InvestmentAsset.class));
    }

    @Test
    @DisplayName("createInvestment NO dispara el backfill para FCI Cuotaparte sin seguimiento automático")
    void createInvestment_noBackfill_forManualFci() {
        InvestmentRequest req = new InvestmentRequest(
                "FCI Manual", InvestmentAssetType.FCI_CUOTAPARTES, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 10), null, BigDecimal.ZERO,
                null, false, null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildFciCuotapartesResponse(UUID.randomUUID()));

        investmentService.createInvestment(req, user);

        verify(fciValuationSyncService, never()).backfillValuations(any(InvestmentAsset.class));
    }
}
