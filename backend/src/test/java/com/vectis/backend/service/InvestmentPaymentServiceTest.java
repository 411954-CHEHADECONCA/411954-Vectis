package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentPaymentSource;
import com.vectis.backend.domain.entity.InvestmentPaymentStatus;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ConfirmPaymentRequest;
import com.vectis.backend.dto.ConfirmPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentRequest;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentUpdateRequest;
import com.vectis.backend.dto.PendingPaymentResponse;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentPaymentNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.InvestmentPaymentRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentPaymentService")
class InvestmentPaymentServiceTest {

    @InjectMocks
    private InvestmentPaymentService service;

    @Mock private InvestmentRepository        investmentRepository;
    @Mock private InvestmentPaymentRepository paymentRepository;
    @Mock private TransactionRepository       transactionRepository;
    @Mock private MonthPeriodService          monthPeriodService;

    private User user;
    private UUID userId;
    private Account account;
    private InvestmentAsset asset;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        assetId = UUID.randomUUID();

        user = User.builder().id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash").build();

        account = Account.builder()
                .id(UUID.randomUUID()).user(user).name("Cuenta USD").kind("Banco").ccy("USD")
                .balance(new BigDecimal("100000.0000")).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        asset = InvestmentAsset.builder()
                .id(assetId).user(user).account(account).name("AL30").type(InvestmentAssetType.BONO)
                .currency("USD").principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .autoTrack(true).externalId("AL30").includeInCashflow(true)
                .status(InvestmentAssetStatus.ACTIVA)
                .build();

        lenient().when(monthPeriodService.isOpen(any(), anyInt(), anyInt())).thenReturn(true);
    }

    private InvestmentPayment.InvestmentPaymentBuilder basePayment(UUID id, LocalDate cuttingDate) {
        return InvestmentPayment.builder()
                .id(id).investmentAsset(asset).cuttingDate(cuttingDate)
                .rentPer100(new BigDecimal("0.270000")).amortizationPer100(new BigDecimal("8.000000"))
                .currency("USD").source(InvestmentPaymentSource.PPI).status(InvestmentPaymentStatus.PENDIENTE);
    }

    // ─── mergeSchedule ───────────────────────────────────────────────────────

    @Test
    @DisplayName("mergeSchedule: inserta filas nuevas para fechas de corte que aún no existen")
    void mergeSchedule_insertsNewRows() {
        PpiMarketDataClient.PpiBondFlow flow = new PpiMarketDataClient.PpiBondFlow(
                LocalDate.of(2026, 7, 9), new BigDecimal("0.72"), new BigDecimal("0.27"), new BigDecimal("8"), null);
        PpiMarketDataClient.PpiBondEstimate estimate =
                new PpiMarketDataClient.PpiBondEstimate(List.of(flow), "US$", "Dólar", LocalDate.of(2030, 7, 9));

        given(investmentRepository.findById(assetId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, flow.cuttingDate(), InvestmentPaymentSource.PPI)).willReturn(Optional.empty());

        service.mergeSchedule(assetId, estimate, "USD");

        ArgumentCaptor<InvestmentPayment> captor = ArgumentCaptor.forClass(InvestmentPayment.class);
        verify(paymentRepository).save(captor.capture());
        InvestmentPayment saved = captor.getValue();
        assertThat(saved.getCuttingDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(saved.getRentPer100()).isEqualByComparingTo("0.27");
        assertThat(saved.getAmortizationPer100()).isEqualByComparingTo("8");
        assertThat(saved.getResidualAfterPer100()).isEqualByComparingTo("64"); // 0.72*100 - 8
        assertThat(saved.getStatus()).isEqualTo(InvestmentPaymentStatus.PENDIENTE);
        assertThat(saved.getSource()).isEqualTo(InvestmentPaymentSource.PPI);
    }

    @Test
    @DisplayName("mergeSchedule: actualiza una fila PENDIENTE no editada si los montos de PPI cambiaron (bono CER)")
    void mergeSchedule_updatesUneditedPendingRow_whenAmountsChanged() {
        PpiMarketDataClient.PpiBondFlow flow = new PpiMarketDataClient.PpiBondFlow(
                LocalDate.of(2026, 11, 9), new BigDecimal("0.20"), new BigDecimal("7.5"), new BigDecimal("705.9"), null);
        PpiMarketDataClient.PpiBondEstimate estimate =
                new PpiMarketDataClient.PpiBondEstimate(List.of(flow), "AR$", "Peso", LocalDate.of(2026, 11, 9));

        InvestmentPayment existing = basePayment(UUID.randomUUID(), flow.cuttingDate())
                .rentPer100(new BigDecimal("7.055866")).amortizationPer100(new BigDecimal("705.586555"))
                .userEdited(false).status(InvestmentPaymentStatus.PENDIENTE).build();

        given(investmentRepository.findById(assetId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, flow.cuttingDate(), InvestmentPaymentSource.PPI)).willReturn(Optional.of(existing));

        service.mergeSchedule(assetId, estimate, "ARS");

        ArgumentCaptor<InvestmentPayment> captor = ArgumentCaptor.forClass(InvestmentPayment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getRentPer100()).isEqualByComparingTo("7.5");
        assertThat(captor.getValue().getAmortizationPer100()).isEqualByComparingTo("705.9");
    }

    @Test
    @DisplayName("mergeSchedule: nunca pisa una fila con userEdited=true")
    void mergeSchedule_neverOverwritesUserEditedRow() {
        PpiMarketDataClient.PpiBondFlow flow = new PpiMarketDataClient.PpiBondFlow(
                LocalDate.of(2026, 7, 9), new BigDecimal("0.72"), new BigDecimal("0.27"), new BigDecimal("8"), null);
        PpiMarketDataClient.PpiBondEstimate estimate =
                new PpiMarketDataClient.PpiBondEstimate(List.of(flow), "US$", "Dólar", LocalDate.of(2030, 7, 9));

        InvestmentPayment existing = basePayment(UUID.randomUUID(), flow.cuttingDate())
                .rentPer100(new BigDecimal("999")).userEdited(true).build();

        given(investmentRepository.findById(assetId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, flow.cuttingDate(), InvestmentPaymentSource.PPI)).willReturn(Optional.of(existing));

        service.mergeSchedule(assetId, estimate, "USD");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeSchedule: nunca pisa una fila con status distinto de PENDIENTE")
    void mergeSchedule_neverOverwritesNonPendingRow() {
        PpiMarketDataClient.PpiBondFlow flow = new PpiMarketDataClient.PpiBondFlow(
                LocalDate.of(2026, 7, 9), new BigDecimal("0.72"), new BigDecimal("0.27"), new BigDecimal("8"), null);
        PpiMarketDataClient.PpiBondEstimate estimate =
                new PpiMarketDataClient.PpiBondEstimate(List.of(flow), "US$", "Dólar", LocalDate.of(2030, 7, 9));

        InvestmentPayment existing = basePayment(UUID.randomUUID(), flow.cuttingDate())
                .rentPer100(new BigDecimal("999")).status(InvestmentPaymentStatus.COBRADO).build();

        given(investmentRepository.findById(assetId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, flow.cuttingDate(), InvestmentPaymentSource.PPI)).willReturn(Optional.of(existing));

        service.mergeSchedule(assetId, estimate, "USD");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeSchedule: setea paymentCurrency del activo si estaba null")
    void mergeSchedule_setsAssetPaymentCurrency_whenNull() {
        assertThat(asset.getPaymentCurrency()).isNull();
        PpiMarketDataClient.PpiBondEstimate estimate =
                new PpiMarketDataClient.PpiBondEstimate(List.of(), "US$", "Dólar", null);

        given(investmentRepository.findById(assetId)).willReturn(Optional.of(asset));

        service.mergeSchedule(assetId, estimate, "USD");

        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentCurrency()).isEqualTo("USD");
    }

    // ─── getPayments (estimado con units a una fecha intermedia) ────────────

    @Test
    @DisplayName("getPayments: monto estimado = per100 × nominales tenidos a la fecha de corte / 100")
    void getPayments_estimatesAmountWithUnitsHeldAtCuttingDate() {
        // Sin overrides: usa unitsHeldAsOf, que sin movimientos cargados da 0 (ver InvestmentValuationCalculator).
        // Con overrides seteados sí refleja el monto fijado por el usuario.
        InvestmentPayment payment = basePayment(UUID.randomUUID(), LocalDate.of(2026, 7, 9))
                .rentAmountOverride(new BigDecimal("2700.0000"))
                .amortizationAmountOverride(new BigDecimal("80000.0000"))
                .build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findAllByInvestmentAsset_IdOrderByCuttingDateAsc(assetId)).willReturn(List.of(payment));

        List<InvestmentPaymentResponse> result = service.getPayments(assetId, user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedRentAmount()).isEqualByComparingTo("2700.0000");
        assertThat(result.get(0).estimatedAmortizationAmount()).isEqualByComparingTo("80000.0000");
    }

    @Test
    @DisplayName("getPayments: activo inexistente o de otro usuario lanza InvestmentNotFoundException")
    void getPayments_throwsWhenAssetNotFound() {
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayments(assetId, user))
                .isInstanceOf(InvestmentNotFoundException.class);
    }

    // ─── createManualPayment ─────────────────────────────────────────────────

    @Test
    @DisplayName("createManualPayment: crea con fuente MANUAL, userEdited=true y overrides absolutos")
    void createManualPayment_createsWithManualSourceAndOverrides() {
        InvestmentPaymentRequest request = new InvestmentPaymentRequest(
                LocalDate.of(2026, 12, 1), "USD", new BigDecimal("100.00"), new BigDecimal("500.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, request.cuttingDate(), InvestmentPaymentSource.MANUAL)).willReturn(Optional.empty());
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentPaymentResponse result = service.createManualPayment(assetId, request, user);

        assertThat(result.source()).isEqualTo("MANUAL");
        assertThat(result.userEdited()).isTrue();
        assertThat(result.estimatedRentAmount()).isEqualByComparingTo("100.00");
        assertThat(result.estimatedAmortizationAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("createManualPayment: 409 si ya existe un pago manual para esa fecha")
    void createManualPayment_conflictOnDuplicateDate() {
        InvestmentPaymentRequest request = new InvestmentPaymentRequest(
                LocalDate.of(2026, 12, 1), "USD", BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByInvestmentAsset_IdAndCuttingDateAndSource(
                assetId, request.cuttingDate(), InvestmentPaymentSource.MANUAL))
                .willReturn(Optional.of(basePayment(UUID.randomUUID(), request.cuttingDate()).build()));

        assertThatThrownBy(() -> service.createManualPayment(assetId, request, user))
                .isInstanceOf(VectisException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    @DisplayName("createManualPayment: 409 si el activo no es BONO/ON")
    void createManualPayment_conflictWhenNotBondOrOn() {
        InvestmentAsset fciAsset = InvestmentAsset.builder()
                .id(assetId).user(user).name("FCI").type(InvestmentAssetType.FCI)
                .currency("ARS").principal(BigDecimal.TEN).purchaseDate(LocalDate.now()).tna(BigDecimal.TEN)
                .status(InvestmentAssetStatus.ACTIVA).build();
        InvestmentPaymentRequest request = new InvestmentPaymentRequest(
                LocalDate.of(2026, 12, 1), "ARS", BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(fciAsset));

        assertThatThrownBy(() -> service.createManualPayment(assetId, request, user))
                .isInstanceOf(VectisException.class);
    }

    // ─── updatePayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updatePayment: sólo actualiza los campos presentes en el request")
    void updatePayment_updatesOnlyPresentFields() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .rentAmountOverride(null).amortizationAmountOverride(new BigDecimal("1.00")).build();
        InvestmentPaymentUpdateRequest request = new InvestmentPaymentUpdateRequest(null, new BigDecimal("50.00"), null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentPaymentResponse result = service.updatePayment(assetId, paymentId, request, user);

        assertThat(result.cuttingDate()).isEqualTo(LocalDate.of(2026, 7, 9)); // no tocado
        assertThat(result.estimatedRentAmount()).isEqualByComparingTo("50.00"); // actualizado
        assertThat(result.estimatedAmortizationAmount()).isEqualByComparingTo("1.00"); // no tocado
        assertThat(result.userEdited()).isTrue();
    }

    @Test
    @DisplayName("updatePayment: 409 si el pago no está PENDIENTE")
    void updatePayment_conflictWhenNotPending() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.COBRADO).build();
        InvestmentPaymentUpdateRequest request = new InvestmentPaymentUpdateRequest(null, null, null);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updatePayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class);
    }

    // ─── omitPayment / deletePayment ─────────────────────────────────────────

    @Test
    @DisplayName("omitPayment: marca OMITIDO un pago PENDIENTE")
    void omitPayment_marksOmitido() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentPaymentResponse result = service.omitPayment(assetId, paymentId, user);

        assertThat(result.status()).isEqualTo("OMITIDO");
    }

    @Test
    @DisplayName("omitPayment: 409 si el pago no está PENDIENTE")
    void omitPayment_conflictWhenNotPending() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.OMITIDO).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.omitPayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("deletePayment: borra un pago MANUAL + PENDIENTE")
    void deletePayment_deletesManualPending() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .source(InvestmentPaymentSource.MANUAL).status(InvestmentPaymentStatus.PENDIENTE).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        service.deletePayment(assetId, paymentId, user);

        verify(paymentRepository).delete(existing);
    }

    @Test
    @DisplayName("deletePayment: 409 si el pago es de fuente PPI")
    void deletePayment_conflictWhenSourceIsPpi() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build(); // source PPI default

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deletePayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
        verify(paymentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletePayment: 409 si el pago no está PENDIENTE")
    void deletePayment_conflictWhenNotPending() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .source(InvestmentPaymentSource.MANUAL).status(InvestmentPaymentStatus.COBRADO).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deletePayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
    }

    // ─── confirmPayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmPayment: feliz — genera dos transacciones y marca COBRADO")
    void confirmPayment_happyPath_createsTwoTransactionsAndMarksCollected() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .residualAfterPer100(new BigDecimal("64.000000")).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("270.00"), new BigDecimal("8000.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(transactionRepository.save(any())).willAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.transactionsCreated()).isEqualTo(2);
        assertThat(response.payment().status()).isEqualTo("COBRADO");
        assertThat(response.assetCollected()).isFalse(); // residual != 0

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txCaptor.capture());
        List<Transaction> txs = txCaptor.getAllValues();
        assertThat(txs).extracting(Transaction::getInvestmentSourceType)
                .containsExactlyInAnyOrder(InvestmentSourceType.COUPON_RENT, InvestmentSourceType.AMORTIZATION);
        assertThat(txs).allMatch(tx -> tx.getType() == TransactionType.INCOME);
        assertThat(txs).allMatch(tx -> tx.getAccount() == account);
    }

    @Test
    @DisplayName("confirmPayment: sin cuenta de cobro resuelta no genera transacciones pero igual confirma")
    void confirmPayment_withoutAccount_marksCollectedWithoutTransactions() {
        asset.setAccount(null);
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("270.00"), new BigDecimal("8000.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        // Con residual null el fallback consulta pagos pendientes; se stubea PENDIENTE para
        // mantener este test enfocado en el camino "sin cuenta → sin transacciones" (no en el cierre).
        given(paymentRepository.existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE))
                .willReturn(true);

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.transactionsCreated()).isEqualTo(0);
        assertThat(response.payment().status()).isEqualTo("COBRADO");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("confirmPayment: 409 si el mes de la fecha de cobro ya está cerrado")
    void confirmPayment_conflictWhenMonthClosed() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(monthPeriodService.isOpen(userId, 2026, 7)).willReturn(false);

        assertThatThrownBy(() -> service.confirmPayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("confirmPayment: 422 si la fecha de cobro es futura")
    void confirmPayment_unprocessableWhenFutureDate() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build();
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(future, BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.confirmPayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        // No debe tocar la persistencia: rechaza antes de crear transacciones o guardar el pago.
        verifyNoInteractions(transactionRepository);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmPayment: 409 si la moneda de la cuenta de cobro no coincide con la del pago")
    void confirmPayment_conflictWhenCurrencyMismatch() {
        Account arsAccount = Account.builder().id(UUID.randomUUID()).user(user).name("Cuenta ARS")
                .kind("Banco").ccy("ARS").balance(BigDecimal.ZERO).includeInCashflow(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        asset.setAccount(arsAccount);
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).currency("USD").build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.confirmPayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("confirmPayment: 409 si el pago no está PENDIENTE")
    void confirmPayment_conflictWhenPaymentNotPending() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.OMITIDO).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.confirmPayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("confirmPayment: 409 si el activo no está ACTIVA")
    void confirmPayment_conflictWhenAssetNotActive() {
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        UUID paymentId = UUID.randomUUID();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.confirmPayment(assetId, paymentId, request, user))
                .isInstanceOf(VectisException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("confirmPayment: pago final (residual 0) sin más PENDIENTE marca el activo COBRADA")
    void confirmPayment_finalPayment_marksAssetCollected() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2030, 7, 9))
                .residualAfterPer100(BigDecimal.ZERO).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("70.00"), new BigDecimal("8000.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(transactionRepository.save(any())).willAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentRepository.existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE))
                .willReturn(false);

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.assetCollected()).isTrue();
        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvestmentAssetStatus.COBRADA);
    }

    @Test
    @DisplayName("confirmPayment: pago final pero con otros pagos aún PENDIENTE no cierra el activo")
    void confirmPayment_finalPaymentButOthersStillPending_doesNotCollectAsset() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2030, 7, 9))
                .residualAfterPer100(BigDecimal.ZERO).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentRepository.existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE))
                .willReturn(true);

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.assetCollected()).isFalse();
        verify(investmentRepository, never()).save(any(InvestmentAsset.class));
    }

    @Test
    @DisplayName("confirmPayment: residual desconocido (null) sin más PENDIENTE marca el activo COBRADA (fallback)")
    void confirmPayment_nullResidual_noPendingRemaining_marksAssetCollected() {
        UUID paymentId = UUID.randomUUID();
        // basePayment no setea residualAfterPer100 → null (caso donde PPI no lo informa)
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2030, 7, 9)).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), new BigDecimal("70.00"), new BigDecimal("8000.00"));

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(transactionRepository.save(any())).willAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentRepository.existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE))
                .willReturn(false);

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.assetCollected()).isTrue();
        ArgumentCaptor<InvestmentAsset> captor = ArgumentCaptor.forClass(InvestmentAsset.class);
        verify(investmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvestmentAssetStatus.COBRADA);
    }

    @Test
    @DisplayName("confirmPayment: residual desconocido (null) con otros pagos PENDIENTE no cierra el activo")
    void confirmPayment_nullResidual_othersStillPending_doesNotCollect() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2030, 7, 9)).build();
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
                LocalDate.of(2026, 7, 1), BigDecimal.ZERO, BigDecimal.ZERO);

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentRepository.existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE))
                .willReturn(true);

        ConfirmPaymentResponse response = service.confirmPayment(assetId, paymentId, request, user);

        assertThat(response.assetCollected()).isFalse();
        verify(investmentRepository, never()).save(any(InvestmentAsset.class));
    }

    // ─── revertPayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("revertPayment: soft-deletea las transacciones vinculadas y vuelve a PENDIENTE")
    void revertPayment_softDeletesTransactionsAndRevertsToPending() {
        UUID paymentId = UUID.randomUUID();
        Transaction rentTx = Transaction.builder().id(UUID.randomUUID()).user(user).type(TransactionType.INCOME)
                .description("Cupón de renta: AL30").amount(new BigDecimal("270.00")).ccy("USD")
                .account(account).transactionDate(LocalDate.of(2026, 7, 9)).dueDate(LocalDate.of(2026, 7, 9))
                .installment(false).build();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.COBRADO).collectedDate(LocalDate.of(2026, 7, 9))
                .rentTransaction(rentTx).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        InvestmentPaymentResponse result = service.revertPayment(assetId, paymentId, user);

        assertThat(result.status()).isEqualTo("PENDIENTE");
        assertThat(result.collectedDate()).isNull();
        assertThat(rentTx.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(rentTx);
    }

    @Test
    @DisplayName("revertPayment: 409 si el pago no está COBRADO")
    void revertPayment_conflictWhenNotCollected() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9)).build(); // PENDIENTE

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.revertPayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("revertPayment: 409 si el activo ya está COBRADA")
    void revertPayment_conflictWhenAssetAlreadyCollected() {
        asset.setStatus(InvestmentAssetStatus.COBRADA);
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.COBRADO).collectedDate(LocalDate.of(2026, 7, 9)).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.revertPayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
    }

    @Test
    @DisplayName("revertPayment: 409 si el mes de la fecha de cobro ya está cerrado")
    void revertPayment_conflictWhenMonthClosed() {
        UUID paymentId = UUID.randomUUID();
        InvestmentPayment existing = basePayment(paymentId, LocalDate.of(2026, 7, 9))
                .status(InvestmentPaymentStatus.COBRADO).collectedDate(LocalDate.of(2026, 7, 9)).build();

        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)).willReturn(Optional.of(existing));
        given(monthPeriodService.isOpen(userId, 2026, 7)).willReturn(false);

        assertThatThrownBy(() -> service.revertPayment(assetId, paymentId, user))
                .isInstanceOf(VectisException.class);
    }

    // ─── getPendingPayments ───────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingPayments: mapea con assetId/assetName del activo dueño de cada pago")
    void getPendingPayments_mapsAssetContext() {
        InvestmentPayment payment = basePayment(UUID.randomUUID(), LocalDate.of(2026, 7, 1))
                .rentAmountOverride(new BigDecimal("270.00")).amortizationAmountOverride(new BigDecimal("8000.00"))
                .build();
        given(paymentRepository.findPendingByUser(eq(userId), any(LocalDate.class))).willReturn(List.of(payment));

        List<PendingPaymentResponse> result = service.getPendingPayments(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assetId()).isEqualTo(assetId);
        assertThat(result.get(0).assetName()).isEqualTo("AL30");
        assertThat(result.get(0).estimatedRent()).isEqualByComparingTo("270.00");
        assertThat(result.get(0).estimatedAmortization()).isEqualByComparingTo("8000.00");
    }
}
