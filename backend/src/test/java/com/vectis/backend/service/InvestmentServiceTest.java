package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentCollectPreviewResponse;
import com.vectis.backend.dto.InvestmentCollectRequest;
import com.vectis.backend.dto.InvestmentCollectResponse;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.InvestmentDeleteBlockedException;
import com.vectis.backend.exception.InvestmentMovementNotFoundException;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentValuationNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.InvestmentMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.InvestmentMovementRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.domain.entity.InvestmentValuation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
    @Mock private com.vectis.backend.repository.InvestmentPaymentRepository investmentPaymentRepository;
    @Mock private AccountRepository              accountRepository;
    @Mock private InvestmentMapper               investmentMapper;
    @Mock private FciValuationSyncService        fciValuationSyncService;
    @Mock private PpiValuationSyncService        ppiValuationSyncService;
    @Mock private InvestmentPaymentSyncService   investmentPaymentSyncService;
    @Mock private TransactionRepository          transactionRepository;
    @Mock private MonthPeriodService             monthPeriodService;
    @Mock private BalanceService                 balanceService;

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
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Default: mes abierto salvo que un test lo sobreescriba explícitamente para probar el bloqueo.
        lenient().when(monthPeriodService.isOpen(any(), anyInt(), anyInt())).thenReturn(true);
        // Default: saldo amplio salvo que un test lo sobreescriba para probar el bloqueo por fondos insuficientes.
        lenient().when(balanceService.currentBalance(any(), any()))
                .thenReturn(new BigDecimal("999999999.0000"));
        // Default: sin amortizaciones cobradas todavía (fracción residual = 1), salvo que un test
        // de anti doble-contabilización lo sobreescriba.
        lenient().when(investmentPaymentRepository.sumCollectedAmortizationPer100(any()))
                .thenReturn(BigDecimal.ZERO);
    }

    // ─── getInvestments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvestments retorna lista mapeada del usuario")
    void getInvestments_returnsAllForUser() {
        InvestmentAsset asset   = buildAsset(UUID.randomUUID(), null);
        InvestmentResponse resp = buildResponse(asset.getId(), null, null);

        given(investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of(asset));
        given(investmentMapper.toResponse(asset, List.of())).willReturn(resp);

        List<InvestmentResponse> result = investmentService.getInvestments(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(asset.getId());
        verify(investmentMapper, times(1)).toResponse(asset, List.of());
        verify(investmentPaymentRepository, never())
                .findAllByInvestmentAsset_IdInAndStatusOrderByCuttingDateAsc(any(), any());
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
                "Cocos Capital - Clase A",
                null);

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

    @Test
    @DisplayName("createInvestment con fondos insuficientes en la cuenta lanza VectisException 422 y no persiste nada")
    void createInvestment_insufficientFunds_throws422() {
        InvestmentRequest req = buildRequest(accountId);

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("500000.0000"));

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvestment con includeInCashflow=false permite fondos insuficientes (no genera Transaction)")
    void createInvestment_notIncludedInCashflow_ignoresInsufficientFunds() {
        InvestmentRequest req = new InvestmentRequest(
                "LECAP S31G5", InvestmentAssetType.LETRA, "ARS",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 8, 31),
                new BigDecimal("65.00"),
                accountId,
                false,
                null,
                false);
        InvestmentAsset saved = buildAsset(UUID.randomUUID(), account);
        InvestmentResponse resp = buildResponse(saved.getId(), accountId, "Cuenta Galicia");

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(investmentRepository.save(any(InvestmentAsset.class))).willReturn(saved);
        given(investmentMapper.toResponse(saved)).willReturn(resp);

        investmentService.createInvestment(req, user);

        verify(investmentRepository).save(any(InvestmentAsset.class));
        verify(transactionRepository, never()).save(any());
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
                null,
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
                null,
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
                null,
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

    @Test
    @DisplayName("updateInvestment con accountId de otro usuario lanza FORBIDDEN (activo propio, cuenta ajena)")
    void updateInvestment_withAccountOfAnotherUser_throwsForbidden() {
        UUID assetId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(accountRepository.findByIdAndUser_Id(otherAccountId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, buildRequest(otherAccountId), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInvestment sincroniza la Transaction de suscripción vinculada cuando cuenta/moneda/monto cambian y el mes está abierto")
    void updateInvestment_syncsLinkedSuscripcionTransaction_whenMonthOpen() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("PF Original")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("30.0000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        Account newAccount = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Cuenta USD").kind("Banco")
                .ccy("USD").balance(BigDecimal.ZERO).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        // Transacción generada por createInvestment: investmentMovement null + fuente SUSCRIPCION.
        Transaction linkedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .investmentMovement(null)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .account(account)
                .ccy("ARS")
                .amount(new BigDecimal("1000000.0000"))
                .transactionDate(LocalDate.of(2026, 1, 1))
                .build();

        InvestmentRequest req = new InvestmentRequest(
                "PF Actualizado", InvestmentAssetType.PLAZO_FIJO, "USD",
                new BigDecimal("2000000.00"),
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("30.00"),
                newAccount.getId(),
                false,
                null,
                null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(accountRepository.findByIdAndUser_Id(newAccount.getId(), userId)).willReturn(Optional.of(newAccount));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of(linkedTx));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.updateInvestment(assetId, req, user);

        assertThat(linkedTx.getAccount()).isEqualTo(newAccount);
        assertThat(linkedTx.getCcy()).isEqualTo("USD");
        assertThat(linkedTx.getAmount()).isEqualByComparingTo("2000000.00");
        verify(transactionRepository).save(linkedTx);
    }

    @Test
    @DisplayName("updateInvestment que sube el principal por encima del saldo disponible lanza 422 y no persiste nada")
    void updateInvestment_syncLinkedTransaction_insufficientFunds_throws422() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("PF Original")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("30.0000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        Transaction linkedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .investmentMovement(null)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .account(account)
                .ccy("ARS")
                .amount(new BigDecimal("1000000.0000"))
                .transactionDate(LocalDate.of(2026, 1, 1))
                .build();

        // Sube el principal de 1.000.000 a 5.000.000 sobre una cuenta con saldo actual de 1.200.000
        // (ya neto del suscripción vieja, que se "libera" antes de re-chequear).
        InvestmentRequest req = new InvestmentRequest(
                "PF Actualizado", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("5000000.00"),
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("30.00"),
                accountId,
                false,
                null,
                null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of(linkedTx));
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("1200000.0000"));

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(linkedTx.getAmount()).isEqualByComparingTo("1000000.0000");
        verify(transactionRepository, never()).save(any());
        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInvestment con transacción vinculada en mes cerrado lanza 409 y no persiste ningún cambio")
    void updateInvestment_blocksSync_whenLinkedTransactionMonthClosed() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("PF Original")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("30.0000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        Transaction linkedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .investmentMovement(null)
                .investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                .account(account)
                .ccy("ARS")
                .amount(new BigDecimal("1000000.0000"))
                .transactionDate(LocalDate.of(2026, 1, 1))
                .build();

        // Sólo cambia el principal (misma cuenta, misma moneda) — igual debe intentar sincronizar.
        InvestmentRequest req = new InvestmentRequest(
                "PF Actualizado", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("2000000.00"),
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("30.00"),
                accountId,
                false,
                null,
                null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of(linkedTx));
        given(monthPeriodService.isOpen(userId, 2026, 1)).willReturn(false);

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // Nada se persistió: ni la Transaction vinculada ni el InvestmentAsset.
        assertThat(linkedTx.getAmount()).isEqualByComparingTo("1000000.0000");
        assertThat(linkedTx.getCcy()).isEqualTo("ARS");
        verify(transactionRepository, never()).save(any());
        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInvestment sin transacción vinculada no intenta sincronizar nada aunque cambien cuenta/moneda/principal")
    void updateInvestment_noLinkedTransaction_skipsSyncSilently() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("PF Original")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("30.0000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        InvestmentRequest req = new InvestmentRequest(
                "PF Actualizado", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("2000000.00"),
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("30.00"),
                accountId,
                false,
                null,
                null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of());
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.updateInvestment(assetId, req, user);

        assertThat(asset.getPrincipal()).isEqualByComparingTo("2000000.00");
        verify(transactionRepository, never()).save(any());
        verify(investmentRepository).save(asset);
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
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("500000.00"), null, null);
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
                LocalDate.of(2026, 3, 1), InvestmentMovementType.RESCATE, new BigDecimal("200000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMovement SUSCRIPCION con fondos insuficientes en la cuenta lanza VectisException 422")
    void addMovement_suscripcion_insufficientFunds_throws422() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        asset.setAccount(account);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("500000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(balanceService.currentBalance(account, userId)).willReturn(new BigDecimal("100000.0000"));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(investmentRepository, never()).save(any());
        verify(movementRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMovement lanza InvestmentNotFoundException cuando el activo no pertenece al usuario")
    void addMovement_notOwner_throwsNotFound() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("100000.00"), null, null);

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
                null,
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
                new BigDecimal("600.000000"),
                null);

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
                null,
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
        // Carga manual explícita del usuario: nunca debe quedar marcada como auto-generada,
        // para que un sync de mercado posterior no la pise.
        assertThat(asset.getValuations().get(0).isAutoGenerated()).isFalse();
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
                new BigDecimal("200.000000"),
                null);

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
                LocalDate.of(2026, 4, 1), InvestmentMovementType.REVALUO, new BigDecimal("15000.00"), null, null);
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
                LocalDate.of(2026, 4, 1), InvestmentMovementType.REVALUO, new BigDecimal("15000.00"), null, null);
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
                null, true, "Alpha Pesos - Clase A", null);

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
                null, false, null, null);

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
                null, false, null, null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildFciCuotapartesResponse(UUID.randomUUID()));

        investmentService.createInvestment(req, user);

        verify(fciValuationSyncService, never()).backfillValuations(any(InvestmentAsset.class));
    }

    @Test
    @DisplayName("createInvestment dispara el backfill PPI para LETRA con seguimiento automático")
    void createInvestment_triggersPpiBackfill_forAutoTrackedLetra() {
        InvestmentRequest req = new InvestmentRequest(
                "LECAP S31G5", InvestmentAssetType.LETRA, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 2, 20), LocalDate.of(2026, 8, 31), BigDecimal.ZERO,
                null, true, "S31G5", null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildResponse(UUID.randomUUID(), null, null));
        given(ppiValuationSyncService.backfillValuations(any(InvestmentAsset.class))).willReturn(90);

        investmentService.createInvestment(req, user);

        verify(ppiValuationSyncService).backfillValuations(any(InvestmentAsset.class));
        verify(fciValuationSyncService, never()).backfillValuations(any(InvestmentAsset.class));
    }

    // ─── Valuación histórica por operación (susc/resc) en familia cuotapartes ──

    @Test
    @DisplayName("addMovement CP con pricePerUnit persiste una valuación a la fecha del movimiento")
    void addMovement_cp_withExplicitPrice_persistsValuation() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), new BigDecimal("400.000000"), new BigDecimal("1250.5000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        InvestmentValuation val = asset.getValuations().get(0);
        assertThat(val.getValuationDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(val.getPricePerUnit()).isEqualByComparingTo("1250.5000");
        // La valuación derivada de una operación (suscripción/rescate) es auto-generada: un precio
        // de mercado real de la misma fecha debe poder pisarla más adelante.
        assertThat(val.isAutoGenerated()).isTrue();
    }

    @Test
    @DisplayName("addMovement CP con una valuación existente a esa fecha la actualiza (upsert, no duplica)")
    void addMovement_cp_existingValuationSameDate_isUpdated() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        asset.getValuations().add(InvestmentValuation.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 3, 15))
                .pricePerUnit(new BigDecimal("1000.0000")).source("MANUAL").build());

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), new BigDecimal("400.000000"), new BigDecimal("1300.0000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("1300.0000");
    }

    @Test
    @DisplayName("addMovement CP sin pricePerUnit deriva el precio de monto/cuotapartes")
    void addMovement_cp_withoutPrice_derivesFromAmountAndUnits() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), new BigDecimal("400.000000"), null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        // 500000 / 400 = 1250.0000
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("1250.0000");
    }

    @Test
    @DisplayName("addMovement CP sin precio ni cuotapartes NO persiste valuación")
    void addMovement_cp_withoutPriceNorUnits_noValuation() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).isEmpty();
    }

    @Test
    @DisplayName("addMovement FCI (Cuenta Remunerada) NO persiste valuación aunque llegue pricePerUnit (aislamiento)")
    void addMovement_fci_neverPersistsValuation() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), null, new BigDecimal("1250.5000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).isEmpty();
    }

    @Test
    @DisplayName("addMovement CP RESCATE persiste su valuación a la fecha (marca de precio de la operación)")
    void addMovement_cp_rescate_persistsValuation() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("500000.00"));
        asset.getMovements().add(InvestmentMovement.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00")).units(new BigDecimal("400.000000")).build());

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 4, 1), InvestmentMovementType.RESCATE,
                new BigDecimal("100000.00"), new BigDecimal("80.000000"), new BigDecimal("1300.0000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        assertThat(asset.getValuations().get(0).getValuationDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("1300.0000");
    }

    @Test
    @DisplayName("addMovement CP NO pisa una valuación de mercado (PPI) a la misma fecha (precedencia de fuente)")
    void addMovement_cp_doesNotOverwriteMarketValuation() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        asset.getValuations().add(InvestmentValuation.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 3, 15))
                .pricePerUnit(new BigDecimal("965.9000")).source("PPI").build());

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), new BigDecimal("400.000000"), new BigDecimal("1300.0000"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations()).hasSize(1);
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("965.9000");
        assertThat(asset.getValuations().get(0).getSource()).isEqualTo("PPI");
    }

    @Test
    @DisplayName("addMovement CP redondea el pricePerUnit explícito a 4 decimales (HALF_EVEN)")
    void addMovement_cp_scalesExplicitPriceTo4Decimals() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 15), InvestmentMovementType.SUSCRIPCION,
                new BigDecimal("500000.00"), new BigDecimal("400.000000"), new BigDecimal("1234.56789012"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.addMovement(assetId, req, user);

        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("1234.5679");
    }

    @Test
    @DisplayName("deleteMovement CP elimina la valuación MANUAL huérfana de la operación")
    void deleteMovement_cp_removesOrphanOperationValuation() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("500000.00"));
        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId).investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 3, 15))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00")).units(new BigDecimal("400.000000")).build();
        asset.getMovements().add(movement);
        asset.getValuations().add(InvestmentValuation.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 3, 15))
                .pricePerUnit(new BigDecimal("1250.0000")).source("MANUAL").build());

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.deleteMovement(assetId, movId, user);

        assertThat(asset.getMovements()).isEmpty();
        assertThat(asset.getValuations()).isEmpty();
    }

    @Test
    @DisplayName("deleteMovement CP preserva la valuación de mercado (PPI) a la fecha del movimiento borrado")
    void deleteMovement_cp_preservesMarketValuation() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, new BigDecimal("500000.00"));
        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId).investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 3, 15))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00")).units(new BigDecimal("400.000000")).build();
        asset.getMovements().add(movement);
        asset.getValuations().add(InvestmentValuation.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 3, 15))
                .pricePerUnit(new BigDecimal("965.9000")).source("PPI").build());

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildFciCuotapartesResponse(assetId));

        investmentService.deleteMovement(assetId, movId, user);

        assertThat(asset.getValuations()).hasSize(1);
        assertThat(asset.getValuations().get(0).getSource()).isEqualTo("PPI");
    }

    // ─── Vínculo con Transaction (AF411954 — Vincular Inversiones con Cuentas) ─

    @Test
    @DisplayName("createInvestment con cuenta vinculada y principal>0 crea una Transaction EXPENSE vinculada (SUSCRIPCION)")
    void createInvestment_withAccountAndPositivePrincipal_createsLinkedTransaction() {
        InvestmentRequest req = new InvestmentRequest(
                "PF 30 días", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                accountId, false, null, null);

        InvestmentAsset saved = InvestmentAsset.builder()
                .id(UUID.randomUUID()).user(user).account(account)
                .name("PF 30 días").type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("500000.00"))
                .purchaseDate(LocalDate.of(2026, 1, 10))
                .tna(new BigDecimal("60.00"))
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(investmentRepository.save(any(InvestmentAsset.class))).willReturn(saved);
        given(investmentMapper.toResponse(saved)).willReturn(buildResponse(saved.getId(), accountId, "Cuenta Galicia"));

        investmentService.createInvestment(req, user);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.getAmount()).isEqualByComparingTo("500000.00");
        assertThat(tx.getAccount()).isEqualTo(account);
        assertThat(tx.getInvestmentAsset()).isEqualTo(saved);
        assertThat(tx.getInvestmentMovement()).isNull();
        assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.SUSCRIPCION);
    }

    @Test
    @DisplayName("createInvestment con includeInCashflow=false NO crea Transaction aunque tenga cuenta y principal>0")
    void createInvestment_includeInCashflowFalse_skipsTransaction() {
        InvestmentRequest req = new InvestmentRequest(
                "PF 30 días", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                accountId, false, null, false);

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildResponse(UUID.randomUUID(), accountId, "Cuenta Galicia"));

        investmentService.createInvestment(req, user);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvestment sin cuenta vinculada NO crea Transaction aunque el principal sea >0")
    void createInvestment_noAccount_skipsTransaction() {
        InvestmentRequest req = new InvestmentRequest(
                "PF 30 días", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                null, false, null, null);

        given(investmentRepository.save(any(InvestmentAsset.class))).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any(InvestmentAsset.class)))
                .willReturn(buildResponse(UUID.randomUUID(), null, null));

        investmentService.createInvestment(req, user);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvestment con moneda de inversión distinta a la de la cuenta lanza VectisException 409")
    void createInvestment_currencyMismatch_throwsConflict() {
        InvestmentRequest req = new InvestmentRequest(
                "PF USD", InvestmentAssetType.PLAZO_FIJO, "USD",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                accountId, false, null, null);

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account)); // account.ccy = ARS

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvestment con mes cerrado en la fecha de compra rechaza toda la creación (409)")
    void createInvestment_closedMonth_throwsConflict() {
        InvestmentRequest req = new InvestmentRequest(
                "PF 30 días", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 10), null, new BigDecimal("60.00"),
                accountId, false, null, null);

        given(accountRepository.findByIdAndUser_Id(accountId, userId)).willReturn(Optional.of(account));
        given(monthPeriodService.isOpen(userId, 2026, 1)).willReturn(false);

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMovement SUSCRIPCION con cuenta vinculada crea una Transaction EXPENSE vinculada al movimiento")
    void addMovement_suscripcion_withAccount_createsLinkedTransaction() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        asset.setAccount(account);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 5), InvestmentMovementType.SUSCRIPCION, new BigDecimal("300000.00"), null, null);

        InvestmentMovement savedMovement = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 5))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("300000.00"))
                .build();

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.saveAndFlush(any(InvestmentMovement.class))).willReturn(savedMovement);
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, accountId, "Cuenta Galicia"));

        investmentService.addMovement(assetId, req, user);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.getAmount()).isEqualByComparingTo("300000.00");
        assertThat(tx.getInvestmentMovement()).isEqualTo(savedMovement);
        assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.SUSCRIPCION);
    }

    @Test
    @DisplayName("addMovement RESCATE con cuenta vinculada crea una Transaction INCOME vinculada al movimiento")
    void addMovement_rescate_withAccount_createsIncomeTransaction() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));
        asset.setAccount(account);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 3, 1), InvestmentMovementType.RESCATE, new BigDecimal("100000.00"), null, null);

        InvestmentMovement savedMovement = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 3, 1))
                .type(InvestmentMovementType.RESCATE)
                .amount(new BigDecimal("100000.00"))
                .build();

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.saveAndFlush(any(InvestmentMovement.class))).willReturn(savedMovement);
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, accountId, "Cuenta Galicia"));

        investmentService.addMovement(assetId, req, user);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.RESCATE);
    }

    @Test
    @DisplayName("addMovement SUSCRIPCION/RESCATE en un mes cerrado lanza VectisException 409 antes de mutar nada")
    void addMovement_closedMonth_throwsConflict() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 5), InvestmentMovementType.SUSCRIPCION, new BigDecimal("300000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(monthPeriodService.isOpen(userId, 2026, 1)).willReturn(false);

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(asset.getMovements()).isEmpty();
        verify(investmentRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAssetType.class, names = {"FCI", "FCI_CUOTAPARTES"})
    @DisplayName("addMovement REVALUO nunca genera Transaction ni gatea por mes cerrado, para ningún tipo que lo soporte")
    void addMovement_revaluo_neverCreatesTransaction(InvestmentAssetType type) {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = type == InvestmentAssetType.FCI
                ? buildFciAsset(assetId, new BigDecimal("500000.00"))
                : buildFciCuotapartesAsset(assetId, new BigDecimal("500000.00"));
        asset.setAccount(account);

        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 4, 1), InvestmentMovementType.REVALUO, new BigDecimal("15000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.addMovement(assetId, req, user);

        verify(transactionRepository, never()).save(any());
        verify(movementRepository, never()).saveAndFlush(any());
        verify(monthPeriodService, never()).isOpen(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("deleteMovement revierte (soft-delete) la Transaction vinculada cuando su mes está abierto")
    void deleteMovement_revertsOpenMonthTransaction() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId).movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00")).build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));
        asset.getMovements().add(movement);

        Transaction linkedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionDate(LocalDate.of(2026, 1, 1))
                .build();

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movId)).willReturn(Optional.of(linkedTx));
        given(investmentRepository.save(asset)).willReturn(asset);
        given(investmentMapper.toResponse(asset)).willReturn(buildResponse(assetId, null, null));

        investmentService.deleteMovement(assetId, movId, user);

        assertThat(linkedTx.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(linkedTx);
    }

    @Test
    @DisplayName("deleteMovement bloquea el borrado (409) cuando la Transaction vinculada está en un mes cerrado")
    void deleteMovement_blocksOnClosedMonthTransaction() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();

        InvestmentMovement movement = InvestmentMovement.builder()
                .id(movId).movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("500000.00")).build();

        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));
        asset.getMovements().add(movement);

        Transaction linkedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionDate(LocalDate.of(2026, 1, 1))
                .build();

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(movementRepository.findByIdAndInvestmentAsset_Id(movId, assetId)).willReturn(Optional.of(movement));
        given(transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movId)).willReturn(Optional.of(linkedTx));
        given(monthPeriodService.isOpen(userId, 2026, 1)).willReturn(false);

        assertThatThrownBy(() -> investmentService.deleteMovement(assetId, movId, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(asset.getMovements()).hasSize(1);
        verify(investmentRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteInvestment bloquea (409) cuando alguna Transaction vinculada cae en un mes cerrado")
    void deleteInvestment_blocksWhenAnyLinkedTxClosed() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);

        Transaction openTx   = Transaction.builder().id(UUID.randomUUID()).transactionDate(LocalDate.of(2026, 3, 1)).build();
        Transaction closedTx = Transaction.builder().id(UUID.randomUUID()).transactionDate(LocalDate.of(2026, 1, 1)).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of(openTx, closedTx));
        given(monthPeriodService.isOpen(userId, 2026, 3)).willReturn(true);
        given(monthPeriodService.isOpen(userId, 2026, 1)).willReturn(false);

        assertThatThrownBy(() -> investmentService.deleteInvestment(assetId, user))
                .isInstanceOf(InvestmentDeleteBlockedException.class);

        verify(investmentRepository, never()).delete(any());
        assertThat(openTx.getDeletedAt()).isNull();
        assertThat(closedTx.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("deleteInvestment revierte todas las Transaction vinculadas y borra el activo cuando todas están en meses abiertos")
    void deleteInvestment_succeedsAndRevertsWhenAllOpen() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);

        Transaction tx1 = Transaction.builder().id(UUID.randomUUID()).transactionDate(LocalDate.of(2026, 3, 1)).build();
        Transaction tx2 = Transaction.builder().id(UUID.randomUUID()).transactionDate(LocalDate.of(2026, 2, 1)).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of(tx1, tx2));

        investmentService.deleteInvestment(assetId, user);

        assertThat(tx1.getDeletedAt()).isNotNull();
        assertThat(tx2.getDeletedAt()).isNotNull();
        verify(transactionRepository).saveAll(List.of(tx1, tx2));
        verify(investmentRepository).delete(asset);
    }

    @Test
    @DisplayName("collectInvestment PLAZO_FIJO en mes abierto marca COBRADA, setea collectDate y crea capital+rendimiento")
    void collectInvestment_plazoFijo_marksCollected_createsTwoTransactions() {
        UUID assetId = UUID.randomUUID();
        LocalDate purchaseDate = LocalDate.of(2026, 1, 1);
        LocalDate collectDate  = purchaseDate.plusDays(100);
        // TNA = 36.5% -> tasa diaria exacta = 0.365/365 = 0.001 -> interés = principal * 0.001 * 100 = 10% del capital.
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account)
                .name("Plazo Fijo Test")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(purchaseDate)
                .tna(new BigDecimal("36.5000"))
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        InvestmentCollectRequest req = new InvestmentCollectRequest(collectDate, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);

        InvestmentCollectResponse result = investmentService.collectInvestment(assetId, req, user);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        List<Transaction> txs = txCaptor.getAllValues();
        assertThat(txs).hasSize(2);
        assertThat(txs).allMatch(tx -> tx.getTransactionDate().equals(collectDate));
        assertThat(txs).allMatch(tx -> tx.getType() == TransactionType.INCOME);
        assertThat(txs).anySatisfy(tx -> {
            assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.COLLECTION_CAPITAL);
            assertThat(tx.getAmount()).isEqualByComparingTo("1000000.0000");
        });
        assertThat(txs).anySatisfy(tx -> {
            assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.COLLECTION_YIELD);
            assertThat(tx.getAmount()).isEqualByComparingTo("100000.0000");
        });

        assertThat(asset.getStatus()).isEqualTo(InvestmentAssetStatus.COBRADA);
        assertThat(asset.getCollectDate()).isEqualTo(collectDate);
        assertThat(asset.getCollectedAt()).isNotNull();
        verify(investmentRepository).save(asset);

        assertThat(result.investmentId()).isEqualTo(assetId);
        assertThat(result.capital()).isEqualByComparingTo("1000000.0000");
        assertThat(result.rendimiento()).isEqualByComparingTo("100000.0000");
        assertThat(result.amount()).isEqualByComparingTo("1100000.0000");
        assertThat(result.currency()).isEqualTo("ARS");
        assertThat(result.transactionCreated()).isTrue();
        assertThat(result.collectDate()).isEqualTo(collectDate);
        assertThat(result.status()).isEqualTo("COBRADA");
    }

    @Test
    @DisplayName("collectInvestment con fecha en mes cerrado lanza 409 y no persiste nada")
    void collectInvestment_monthClosed_throws409_persistsNothing() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, account);
        LocalDate collectDate = LocalDate.of(2026, 2, 15);
        InvestmentCollectRequest req = new InvestmentCollectRequest(collectDate, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(monthPeriodService.isOpen(userId, 2026, 2)).willReturn(false);

        assertThatThrownBy(() -> investmentService.collectInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(asset.getStatus()).isEqualTo(InvestmentAssetStatus.ACTIVA);
        verify(transactionRepository, never()).save(any());
        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("collectInvestment con rendimiento override negativo lanza 422 y no persiste nada")
    void collectInvestment_negativeOverride_throws422_persistsNothing() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account)
                .name("Plazo Fijo Test")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("36.5000"))
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        InvestmentCollectRequest req = new InvestmentCollectRequest(
                LocalDate.of(2026, 4, 11), new BigDecimal("-1.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.collectInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(asset.getStatus()).isEqualTo(InvestmentAssetStatus.ACTIVA);
        verify(transactionRepository, never()).save(any());
        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("collectInvestment sobre un activo ya COBRADA lanza 409")
    void collectInvestment_alreadyCollected_throws409() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, account);
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        InvestmentCollectRequest req = new InvestmentCollectRequest(LocalDate.of(2026, 3, 1), null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.collectInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("collectInvestment FCI con override de rendimiento usa el valor dado, no el precálculo")
    void collectInvestment_fci_overrideRendimiento_usesGivenValue() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("1030000.0000"));
        asset.setAccount(account);
        asset.setIncludeInCashflow(true);

        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .build();
        InvestmentMovement revaluo = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 2, 1))
                .type(InvestmentMovementType.REVALUO)
                .amount(new BigDecimal("30000.00"))
                .build();
        asset.getMovements().add(suscripcion);
        asset.getMovements().add(revaluo);

        LocalDate collectDate = LocalDate.of(2026, 2, 1); // = fecha del último movimiento -> sin interés adicional
        InvestmentCollectRequest req = new InvestmentCollectRequest(collectDate, new BigDecimal("99999.99"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);

        InvestmentCollectResponse result = investmentService.collectInvestment(assetId, req, user);

        assertThat(result.capital()).isEqualByComparingTo("1000000.0000");
        assertThat(result.rendimiento()).isEqualByComparingTo("99999.99");
        assertThat(result.amount()).isEqualByComparingTo("1099999.99");
    }

    @Test
    @DisplayName("collectInvestment familia cuotapartes (LETRA): capital=principal, rendimiento=valorAsOf-capital, ignora override del request")
    void collectInvestment_cuotaparteFamily_ignoresOverride() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, account); // LETRA, principal=1.000.000

        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 15))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .units(new BigDecimal("1000000.000000"))
                .build();
        asset.getMovements().add(suscripcion);

        InvestmentValuation valuation = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 6, 1))
                .pricePerUnit(new BigDecimal("1.1500"))
                .source("PPI")
                .build();
        asset.getValuations().add(valuation);

        LocalDate collectDate = LocalDate.of(2026, 6, 15);
        // El override debe ignorarse por completo para la familia cuotapartes.
        InvestmentCollectRequest req = new InvestmentCollectRequest(collectDate, new BigDecimal("12345.67"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);

        InvestmentCollectResponse result = investmentService.collectInvestment(assetId, req, user);

        // Valor de mercado a la fecha = tenencia(1.000.000) x último precio <= fecha (1.15) = 1.150.000.
        assertThat(result.capital()).isEqualByComparingTo("1000000.0000");
        assertThat(result.rendimiento()).isEqualByComparingTo("150000.0000");
        assertThat(result.amount()).isEqualByComparingTo("1150000.0000");
    }

    @ParameterizedTest
    @EnumSource(value = InvestmentAssetType.class,
            names = {"FCI_CUOTAPARTES", "LETRA", "BONO", "ON"})
    @DisplayName("collectInvestment familia cuotapartes con pérdida de mercado: postea EXPENSE por la pérdida, no INCOME, sin transacción fantasma")
    void collectInvestment_cuotaparteFamily_marketLoss_postsExpenseNotIncome(InvestmentAssetType type) {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account)
                .name("Activo con pérdida")
                .type(type).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .maturityDate(LocalDate.of(2026, 8, 31))
                .tna(BigDecimal.ZERO)
                .includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        InvestmentMovement suscripcion = InvestmentMovement.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .movementDate(LocalDate.of(2026, 1, 15))
                .type(InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("1000000.00"))
                .units(new BigDecimal("1000000.000000"))
                .build();
        asset.getMovements().add(suscripcion);

        // Precio de mercado a la fecha de cobro por debajo del costo de adquisición (1.00): pérdida real.
        InvestmentValuation valuation = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 6, 1))
                .pricePerUnit(new BigDecimal("0.8500"))
                .source("PPI")
                .build();
        asset.getValuations().add(valuation);

        LocalDate collectDate = LocalDate.of(2026, 6, 15);
        InvestmentCollectRequest req = new InvestmentCollectRequest(collectDate, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(asset)).willReturn(asset);

        InvestmentCollectResponse result = investmentService.collectInvestment(assetId, req, user);

        // Valor de mercado = 1.000.000 unidades x 0.85 = 850.000 -> rendimiento = 850.000 - 1.000.000 = -150.000
        assertThat(result.capital()).isEqualByComparingTo("1000000.0000");
        assertThat(result.rendimiento()).isEqualByComparingTo("-150000.0000");
        assertThat(result.amount()).isEqualByComparingTo("850000.0000");
        assertThat(result.transactionCreated()).isTrue();

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        List<Transaction> txs = txCaptor.getAllValues();
        assertThat(txs).hasSize(2);

        assertThat(txs).anySatisfy(tx -> {
            assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.COLLECTION_CAPITAL);
            assertThat(tx.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(tx.getAmount()).isEqualByComparingTo("1000000.0000");
        });
        assertThat(txs).anySatisfy(tx -> {
            assertThat(tx.getInvestmentSourceType()).isEqualTo(InvestmentSourceType.COLLECTION_YIELD);
            assertThat(tx.getType()).isEqualTo(TransactionType.EXPENSE);
            assertThat(tx.getAmount()).isEqualByComparingTo("150000.0000");
        });
        // No debe haber ninguna transacción adicional/fantasma más allá de capital + pérdida.
        assertThat(txs).noneMatch(tx -> tx.getAmount().signum() < 0);
    }

    // ─── previewCollect ───────────────────────────────────────────────────────

    @Test
    @DisplayName("previewCollect PLAZO_FIJO/FCI marca editableRendimiento=true")
    void previewCollect_plazoFijo_editableRendimientoTrue() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user)
                .name("Plazo Fijo Test")
                .type(InvestmentAssetType.PLAZO_FIJO).currency("ARS")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(new BigDecimal("36.5000"))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        InvestmentCollectPreviewResponse preview =
                investmentService.previewCollect(assetId, LocalDate.of(2026, 4, 11), user);

        assertThat(preview.editableRendimiento()).isTrue();
        assertThat(preview.capital()).isEqualByComparingTo("1000000.0000");
        assertThat(preview.rendimiento()).isEqualByComparingTo("100000.0000");
        assertThat(preview.total()).isEqualByComparingTo("1100000.0000");
        assertThat(preview.currency()).isEqualTo("ARS");
    }

    @Test
    @DisplayName("previewCollect familia cuotapartes marca editableRendimiento=false")
    void previewCollect_cuotaparteFamily_editableRendimientoFalse() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null); // LETRA

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        InvestmentCollectPreviewResponse preview =
                investmentService.previewCollect(assetId, LocalDate.of(2026, 6, 1), user);

        assertThat(preview.editableRendimiento()).isFalse();
    }

    @Test
    @DisplayName("previewCollect sobre activo no encontrado lanza InvestmentNotFoundException")
    void previewCollect_notFound_throws404() {
        UUID assetId = UUID.randomUUID();
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.previewCollect(assetId, LocalDate.of(2026, 6, 1), user))
                .isInstanceOf(InvestmentNotFoundException.class);
    }

    @Test
    @DisplayName("previewCollect sobre activo ya COBRADA lanza 409")
    void previewCollect_alreadyCollected_throws409() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);
        asset.setStatus(InvestmentAssetStatus.COBRADA);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.previewCollect(assetId, LocalDate.of(2026, 6, 1), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ─── Guardas: mutaciones bloqueadas sobre activo ya COBRADA ──────────────

    @Test
    @DisplayName("updateInvestment sobre activo COBRADA lanza 409")
    void updateInvestment_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);
        asset.setStatus(InvestmentAssetStatus.COBRADA);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, buildRequest(null), user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMovement sobre activo COBRADA lanza 409")
    void addMovement_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, BigDecimal.ZERO);
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        InvestmentMovementRequest req = new InvestmentMovementRequest(
                LocalDate.of(2026, 1, 1), InvestmentMovementType.SUSCRIPCION, new BigDecimal("500000.00"), null, null);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addMovement(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMovement sobre activo COBRADA lanza 409")
    void updateMovement_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("1000000.00"));
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        InvestmentMovementUpdateRequest req = new InvestmentMovementUpdateRequest(null, new BigDecimal("100.00"));

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.updateMovement(assetId, movId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteMovement sobre activo COBRADA lanza 409")
    void deleteMovement_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        UUID movId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciAsset(assetId, new BigDecimal("500000.00"));
        asset.setStatus(InvestmentAssetStatus.COBRADA);

        given(investmentRepository.findWithMovementsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.deleteMovement(assetId, movId, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addValuation sobre activo COBRADA lanza 409")
    void addValuation_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1), new BigDecimal("1500.00"));

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.addValuation(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateValuation sobre activo COBRADA lanza 409")
    void updateValuation_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        InvestmentValuationRequest req = new InvestmentValuationRequest(
                LocalDate.of(2026, 6, 1), new BigDecimal("1500.00"));

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.updateValuation(assetId, valId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteValuation sobre activo COBRADA lanza 409")
    void deleteValuation_collected_throws409() {
        UUID assetId = UUID.randomUUID();
        UUID valId   = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, BigDecimal.ZERO);
        asset.setStatus(InvestmentAssetStatus.COBRADA);

        given(investmentRepository.findWithValuationsByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.deleteValuation(assetId, valId, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteInvestment sobre activo COBRADA sigue funcionando (única acción habilitada además de cobrar)")
    void deleteInvestment_collected_stillWorks() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null);
        asset.setStatus(InvestmentAssetStatus.COBRADA);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(assetId))
                .willReturn(List.of());

        investmentService.deleteInvestment(assetId, user);

        verify(investmentRepository).delete(asset);
    }

    // ─── resolvePaymentAccount (cuenta de cobro de renta/amortización para BONO/ON) ──

    @Test
    @DisplayName("createInvestment con paymentAccountId de otro usuario lanza 403")
    void createInvestment_paymentAccountNotOwned_throws403() {
        UUID paymentAccountId = UUID.randomUUID();
        InvestmentRequest req = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "ARS",
                new BigDecimal("100000.00"), LocalDate.of(2026, 1, 15), null,
                BigDecimal.ZERO, null, false, "AL30", true,
                paymentAccountId, "USD");

        given(accountRepository.findByIdAndUser_Id(paymentAccountId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("createInvestment con paymentAccountId cuya moneda no coincide con paymentCurrency lanza 409")
    void createInvestment_paymentAccountCurrencyMismatch_throws409() {
        Account usdAccount = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Cuenta ARS").kind("Banco").ccy("ARS")
                .balance(BigDecimal.ZERO).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        InvestmentRequest req = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "ARS",
                new BigDecimal("100000.00"), LocalDate.of(2026, 1, 15), null,
                BigDecimal.ZERO, null, false, "AL30", true,
                usdAccount.getId(), "USD");

        given(accountRepository.findByIdAndUser_Id(usdAccount.getId(), userId)).willReturn(Optional.of(usdAccount));

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("createInvestment con paymentCurrency para un tipo que no es BONO/ON lanza 422")
    void createInvestment_paymentFieldsOnNonBondType_throws422() {
        InvestmentRequest req = new InvestmentRequest(
                "Plazo Fijo Test", InvestmentAssetType.PLAZO_FIJO, "ARS",
                new BigDecimal("100000.00"), LocalDate.of(2026, 1, 15), null,
                new BigDecimal("50"), null, false, null, true,
                null, "USD");

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("createInvestment con paymentAccountId válido para BONO lo persiste en el activo")
    void createInvestment_validPaymentAccount_persistsOnAsset() {
        Account usdAccount = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Cuenta USD").kind("Banco").ccy("USD")
                .balance(BigDecimal.ZERO).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        InvestmentRequest req = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 15), null,
                BigDecimal.ZERO, null, false, null, true,
                usdAccount.getId(), "USD");

        given(accountRepository.findByIdAndUser_Id(usdAccount.getId(), userId)).willReturn(Optional.of(usdAccount));
        given(investmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(investmentMapper.toResponse(any())).willReturn(buildResponse(UUID.randomUUID(), null, null));

        investmentService.createInvestment(req, user);

        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentAccount()).isEqualTo(usdAccount);
        assertThat(captor.getValue().getPaymentCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("createInvestment BONO con paymentCurrency distinta a currency y sin cuenta de cobro lanza 422")
    void createInvestment_foreignPaymentCurrencyWithoutAccount_throws422() {
        InvestmentRequest req = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 15), null,
                BigDecimal.ZERO, null, false, null, true,
                null, "USD");

        assertThatThrownBy(() -> investmentService.createInvestment(req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(investmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInvestment BONO con paymentCurrency distinta a currency y sin cuenta de cobro lanza 422")
    void updateInvestment_foreignPaymentCurrencyWithoutAccount_throws422() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildAsset(assetId, null); // ACTIVA
        InvestmentRequest req = new InvestmentRequest(
                "AL30", InvestmentAssetType.BONO, "ARS",
                BigDecimal.ZERO, LocalDate.of(2026, 1, 15), null,
                BigDecimal.ZERO, null, false, null, true,
                null, "USD");

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> investmentService.updateInvestment(assetId, req, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(investmentRepository, never()).save(any());
    }

    // ─── Anti doble-contabilización: collect tras amortizaciones parciales ya cobradas ──

    @Test
    @DisplayName("collectInvestment tras cobrar cupones de amortización descuenta el capital ya devuelto")
    void collectInvestment_afterPartialAmortizationCollected_discountsCapitalAlreadyReturned() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("AL30").type(InvestmentAssetType.BONO)
                .currency("ARS").principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .includeInCashflow(true).status(InvestmentAssetStatus.ACTIVA)
                .build();
        asset.getMovements().add(com.vectis.backend.domain.entity.InvestmentMovement.builder()
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(com.vectis.backend.domain.entity.InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("100000.00")).units(new BigDecimal("100")).build());
        asset.getValuations().add(com.vectis.backend.domain.entity.InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 9, 1)).pricePerUnit(new BigDecimal("920.0000")).source("PPI").build());

        // El usuario ya cobró un cupón de amortización de 8 por 100 nominales vía el calendario de pagos.
        given(investmentPaymentRepository.sumCollectedAmortizationPer100(assetId)).willReturn(new BigDecimal("8"));

        InvestmentCollectRequest request = new InvestmentCollectRequest(LocalDate.of(2026, 9, 1), null);
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentCollectResponse response = investmentService.collectInvestment(assetId, request, user);

        // capital = 100000 * (1 - 8/100) = 92000; valorAsOf = 100*920 = 92000 → rendimiento = 0
        assertThat(response.capital()).isEqualByComparingTo("92000.0000");
        assertThat(response.rendimiento()).isEqualByComparingTo("0.0000");
        assertThat(response.amount()).isEqualByComparingTo("92000.0000");
    }

    @Test
    @DisplayName("collectInvestment sin amortizaciones cobradas usa el capital completo (fracción 1)")
    void collectInvestment_withoutPriorAmortization_usesFullCapital() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("AL30").type(InvestmentAssetType.BONO)
                .currency("ARS").principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .includeInCashflow(true).status(InvestmentAssetStatus.ACTIVA)
                .build();
        asset.getMovements().add(com.vectis.backend.domain.entity.InvestmentMovement.builder()
                .movementDate(LocalDate.of(2026, 1, 1))
                .type(com.vectis.backend.domain.entity.InvestmentMovementType.SUSCRIPCION)
                .amount(new BigDecimal("100000.00")).units(new BigDecimal("100")).build());
        asset.getValuations().add(com.vectis.backend.domain.entity.InvestmentValuation.builder()
                .valuationDate(LocalDate.of(2026, 9, 1)).pricePerUnit(new BigDecimal("1050.0000")).source("PPI").build());

        InvestmentCollectRequest request = new InvestmentCollectRequest(LocalDate.of(2026, 9, 1), null);
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentCollectResponse response = investmentService.collectInvestment(assetId, request, user);

        assertThat(response.capital()).isEqualByComparingTo("100000.0000");
        assertThat(response.rendimiento()).isEqualByComparingTo("5000.0000");
    }

    // ─── getInvestments: batch de amortizaciones cobradas (BONO/ON) ────────────

    @Test
    @DisplayName("getInvestments agrupa en batch las amortizaciones cobradas de los activos BONO/ON")
    void getInvestments_batchesCollectedAmortizationsForBondOrOnAssets() {
        UUID bondId  = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InvestmentAsset bond  = InvestmentAsset.builder()
                .id(bondId).user(user).name("AL30").type(InvestmentAssetType.BONO)
                .currency("ARS").principal(BigDecimal.ZERO).purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO).status(InvestmentAssetStatus.ACTIVA).build();
        InvestmentAsset other = buildAsset(otherId, null); // LETRA, no es BONO/ON

        com.vectis.backend.domain.entity.InvestmentPayment payment =
                com.vectis.backend.domain.entity.InvestmentPayment.builder()
                        .id(UUID.randomUUID()).investmentAsset(bond)
                        .cuttingDate(LocalDate.of(2026, 7, 1))
                        .amortizationPer100(new BigDecimal("8.000000"))
                        .status(com.vectis.backend.domain.entity.InvestmentPaymentStatus.COBRADO)
                        .source(com.vectis.backend.domain.entity.InvestmentPaymentSource.PPI)
                        .currency("ARS")
                        .build();

        InvestmentResponse bondResp  = buildResponse(bondId, null, null);
        InvestmentResponse otherResp = buildResponse(otherId, null, null);

        given(investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId))
                .willReturn(List.of(bond, other));
        given(investmentPaymentRepository.findAllByInvestmentAsset_IdInAndStatusOrderByCuttingDateAsc(
                List.of(bondId), com.vectis.backend.domain.entity.InvestmentPaymentStatus.COBRADO))
                .willReturn(List.of(payment));
        given(investmentMapper.toResponse(bond, List.of(payment))).willReturn(bondResp);
        given(investmentMapper.toResponse(other, List.of())).willReturn(otherResp);

        List<InvestmentResponse> result = investmentService.getInvestments(userId);

        assertThat(result).containsExactly(bondResp, otherResp);
        verify(investmentMapper).toResponse(bond, List.of(payment));
        verify(investmentMapper).toResponse(other, List.of());
    }
}
