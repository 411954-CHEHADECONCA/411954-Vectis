package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondEstimate;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondFlow;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentPaymentSyncService")
class InvestmentPaymentSyncServiceTest {

    @InjectMocks
    private InvestmentPaymentSyncService service;

    @Mock private InvestmentRepository     investmentRepository;
    @Mock private PpiMarketDataClient      ppiMarketDataClient;
    @Mock private InvestmentPaymentService investmentPaymentService;

    private User user;
    private UUID userId;
    private UUID assetId;
    private InvestmentAsset asset;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash").build();
        asset = InvestmentAsset.builder()
                .id(assetId).user(user).name("AL30").type(InvestmentAssetType.BONO)
                .currency("USD").principal(new BigDecimal("100000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1)).tna(BigDecimal.ZERO)
                .autoTrack(true).externalId("AL30").includeInCashflow(true)
                .status(InvestmentAssetStatus.ACTIVA)
                .build();
    }

    private PpiBondEstimate estimate(String payCurrency, String issueCurrency) {
        PpiBondFlow flow = new PpiBondFlow(
                LocalDate.of(2027, 7, 9), new BigDecimal("0.72"), new BigDecimal("0.27"), new BigDecimal("8"), null);
        return new PpiBondEstimate(List.of(flow), payCurrency, issueCurrency, LocalDate.of(2030, 7, 9));
    }

    @Test
    @DisplayName("syncSchedule: activo no BONO/ON devuelve el calendario actual sin llamar a PPI")
    void syncSchedule_nonBondOrOn_skipsPpiCall() {
        InvestmentAsset fciAsset = InvestmentAsset.builder()
                .id(assetId).user(user).name("FCI").type(InvestmentAssetType.FCI_CUOTAPARTES)
                .currency("ARS").principal(BigDecimal.TEN).purchaseDate(LocalDate.now()).tna(BigDecimal.ZERO)
                .status(InvestmentAssetStatus.ACTIVA).build();
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(fciAsset));
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        List<InvestmentPaymentResponse> result = service.syncSchedule(assetId, user);

        assertThat(result).isEmpty();
        verifyNoInteractions(ppiMarketDataClient);
        verify(investmentPaymentService).getPayments(assetId, user);
    }

    @Test
    @DisplayName("syncSchedule: sin externalId devuelve el calendario actual sin llamar a PPI")
    void syncSchedule_noExternalId_skipsPpiCall() {
        asset.setExternalId(null);
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        service.syncSchedule(assetId, user);

        verifyNoInteractions(ppiMarketDataClient);
        verify(investmentPaymentService).getPayments(assetId, user);
    }

    @Test
    @DisplayName("syncSchedule: PPI no configurado devuelve el calendario actual sin lanzar")
    void syncSchedule_ppiNotConfigured_degradesGracefully() {
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(ppiMarketDataClient.isConfigured()).willReturn(false);
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        List<InvestmentPaymentResponse> result = service.syncSchedule(assetId, user);

        assertThat(result).isEmpty();
        verify(ppiMarketDataClient, never()).getBondEstimate(any(), any());
        verify(investmentPaymentService, never()).mergeSchedule(any(), any(), any());
    }

    @Test
    @DisplayName("syncSchedule: PPI sin estimate devuelve el calendario actual sin persistir")
    void syncSchedule_ppiReturnsEmpty_degradesGracefully() {
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.getBondEstimate("AL30", asset.getPurchaseDate())).willReturn(Optional.empty());
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        List<InvestmentPaymentResponse> result = service.syncSchedule(assetId, user);

        assertThat(result).isEmpty();
        verify(investmentPaymentService, never()).mergeSchedule(any(), any(), any());
        verify(investmentPaymentService).getPayments(assetId, user);
    }

    @Test
    @DisplayName("syncSchedule: con estimate delega la persistencia (mergeSchedule) con la moneda de pago y relee el calendario")
    void syncSchedule_withEstimate_delegatesMergeAndReads() {
        PpiBondEstimate est = estimate("US$", "Dólar");
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.getBondEstimate("AL30", asset.getPurchaseDate())).willReturn(Optional.of(est));
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        service.syncSchedule(assetId, user);

        verify(investmentPaymentService).mergeSchedule(eq(assetId), eq(est), eq("USD"));
        verify(investmentPaymentService).getPayments(assetId, user);
    }

    @Test
    @DisplayName("syncSchedule: si abbreviationCurrencyPay no se reconoce, usa issueCurrency antes que la currency del activo")
    void syncSchedule_fallsBackToIssueCurrency_whenPayCurrencyUnrecognized() {
        // Activo en ARS para distinguir: si el fallback usara la currency del activo daría ARS;
        // issueCurrency "US$" debe ganar y persistir USD.
        asset.setCurrency("ARS");
        PpiBondEstimate est = estimate("XYZ", "US$");
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.getBondEstimate("AL30", asset.getPurchaseDate())).willReturn(Optional.of(est));
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        service.syncSchedule(assetId, user);

        verify(investmentPaymentService).mergeSchedule(eq(assetId), eq(est), eq("USD"));
    }

    @Test
    @DisplayName("syncSchedule: si mergeSchedule falla, igual relee el calendario y no propaga la excepción")
    void syncSchedule_mergeFails_stillReturnsSchedule() {
        PpiBondEstimate est = estimate("US$", "Dólar");
        given(investmentRepository.findByIdAndUser_Id(assetId, userId)).willReturn(Optional.of(asset));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.getBondEstimate("AL30", asset.getPurchaseDate())).willReturn(Optional.of(est));
        org.mockito.BDDMockito.willThrow(new RuntimeException("boom"))
                .given(investmentPaymentService).mergeSchedule(eq(assetId), eq(est), eq("USD"));
        given(investmentPaymentService.getPayments(assetId, user)).willReturn(List.of());

        List<InvestmentPaymentResponse> result = service.syncSchedule(assetId, user);

        assertThat(result).isEmpty();
        verify(investmentPaymentService).getPayments(assetId, user);
    }
}
