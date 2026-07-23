package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InstrumentDto;
import com.vectis.backend.repository.DoctaInstrumentCacheRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PpiValuationSyncService")
class PpiValuationSyncServiceTest {

    @InjectMocks
    private PpiValuationSyncService service;

    @Mock private PpiMarketDataClient            ppiMarketDataClient;
    @Mock private DoctaInstrumentCacheRepository instrumentCacheRepository;
    @Mock private InvestmentRepository           investmentRepository;
    @Mock private InvestmentValuationRepository  valuationRepository;

    // ─── syncPpiValuations ────────────────────────────────────────────────────

    @Test
    @DisplayName("syncPpiValuations omite todo cuando el cliente no está configurado")
    void syncPpiValuations_skipsAll_whenClientNotConfigured() {
        given(ppiMarketDataClient.isConfigured()).willReturn(false);

        service.syncPpiValuations();

        verify(investmentRepository, never()).findAllByTypesAndAutoTrackTrue(any());
        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncPpiValuations crea valuación cuando el precio está disponible")
    void syncPpiValuations_createsValuation_whenPriceAvailable() {
        UUID assetId = UUID.randomUUID();
        String ticker = "AL30";
        InvestmentAsset asset = buildBonoAsset(assetId, ticker);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(any()))
                .willReturn(List.of(asset));
        // El cierre real es del 2026-01-16 (no "hoy"): la valuación debe fecharse ahí.
        LocalDate cierreReal = LocalDate.of(2026, 1, 16);
        given(ppiMarketDataClient.getPriceForDate(eq(ticker), eq("BONOS"), any(LocalDate.class)))
                .willReturn(Optional.of(new PpiMarketDataClient.DatedPrice(cierreReal, new BigDecimal("1450.5000"))));
        given(valuationRepository.existsByInvestmentAsset_IdAndValuationDate(eq(assetId), any(LocalDate.class)))
                .willReturn(false);

        service.syncPpiValuations();

        ArgumentCaptor<InvestmentValuation> captor = ArgumentCaptor.forClass(InvestmentValuation.class);
        verify(valuationRepository).save(captor.capture());
        InvestmentValuation saved = captor.getValue();
        assertThat(saved.getPricePerUnit()).isEqualByComparingTo("1450.5000");
        assertThat(saved.getSource()).isEqualTo("PPI");
        assertThat(saved.getValuationDate()).isEqualTo(cierreReal); // fecha real del cierre, no hoy
        assertThat(saved.getInvestmentAsset()).isEqualTo(asset);
    }

    @Test
    @DisplayName("syncPpiValuations no crea valuación si ya existe para el día (idempotencia)")
    void syncPpiValuations_skipsAsset_whenValuationAlreadyExistsToday() {
        UUID assetId = UUID.randomUUID();
        String ticker = "AL30";
        InvestmentAsset asset = buildBonoAsset(assetId, ticker);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(any()))
                .willReturn(List.of(asset));
        given(ppiMarketDataClient.getPriceForDate(eq(ticker), eq("BONOS"), any(LocalDate.class)))
                .willReturn(Optional.of(new PpiMarketDataClient.DatedPrice(LocalDate.of(2026, 1, 16), new BigDecimal("1450.5000"))));
        given(valuationRepository.existsByInvestmentAsset_IdAndValuationDate(eq(assetId), any(LocalDate.class)))
                .willReturn(true);

        service.syncPpiValuations();

        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncPpiValuations omite activo cuando externalId es null")
    void syncPpiValuations_skipsAsset_whenExternalIdIsNull() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildBonoAsset(assetId, null);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(any()))
                .willReturn(List.of(asset));

        service.syncPpiValuations();

        verify(ppiMarketDataClient, never()).getPriceForDate(any(), any(), any());
        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncPpiValuations no lanza excepción cuando el cliente falla por activo (resiliencia)")
    void syncPpiValuations_handlesException_gracefully() {
        UUID assetId = UUID.randomUUID();
        String ticker = "AL30";
        InvestmentAsset asset = buildBonoAsset(assetId, ticker);

        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(any()))
                .willReturn(List.of(asset));
        given(ppiMarketDataClient.getPriceForDate(any(), any(), any()))
                .willThrow(new RuntimeException("API error"));

        service.syncPpiValuations();

        verify(valuationRepository, never()).save(any());
    }

    // ─── backfillValuations ───────────────────────────────────────────────────

    @Test
    @DisplayName("backfillValuations agrega una valuación PPI por día de la serie y retorna la cantidad creada")
    void backfillValuations_addsOnePerSeriesDay() {
        InvestmentAsset asset = buildBonoAsset(UUID.randomUUID(), "AL30");
        given(ppiMarketDataClient.getPriceSeries(eq("AL30"), eq("BONOS"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        new PpiMarketDataClient.DatedPrice(LocalDate.of(2026, 1, 15), new BigDecimal("965.9000")),
                        new PpiMarketDataClient.DatedPrice(LocalDate.of(2026, 1, 16), new BigDecimal("963.0000"))));

        int created = service.backfillValuations(asset);

        assertThat(created).isEqualTo(2);
        assertThat(asset.getValuations()).hasSize(2);
        assertThat(asset.getValuations()).allSatisfy(v -> assertThat(v.getSource()).isEqualTo("PPI"));
        assertThat(asset.getValuations().get(0).getValuationDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("backfillValuations saltea las fechas que ya tienen valuación (no duplica)")
    void backfillValuations_skipsExistingDates() {
        InvestmentAsset asset = buildBonoAsset(UUID.randomUUID(), "AL30");
        asset.getValuations().add(InvestmentValuation.builder()
                .id(UUID.randomUUID()).investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 1, 15))
                .pricePerUnit(new BigDecimal("900.0000")).source("MANUAL").build());

        given(ppiMarketDataClient.getPriceSeries(eq("AL30"), eq("BONOS"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        new PpiMarketDataClient.DatedPrice(LocalDate.of(2026, 1, 15), new BigDecimal("965.9000")),
                        new PpiMarketDataClient.DatedPrice(LocalDate.of(2026, 1, 16), new BigDecimal("963.0000"))));

        int created = service.backfillValuations(asset);

        assertThat(created).isEqualTo(1);
        assertThat(asset.getValuations()).hasSize(2);
        // la valuación existente del 15 no se toca
        assertThat(asset.getValuations().get(0).getPricePerUnit()).isEqualByComparingTo("900.0000");
    }

    @Test
    @DisplayName("backfillValuations retorna 0 cuando la serie viene vacía (degrada sin lanzar)")
    void backfillValuations_returnsZero_whenSeriesEmpty() {
        InvestmentAsset asset = buildBonoAsset(UUID.randomUUID(), "AL30");
        given(ppiMarketDataClient.getPriceSeries(eq("AL30"), eq("BONOS"), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of());

        int created = service.backfillValuations(asset);

        assertThat(created).isZero();
        assertThat(asset.getValuations()).isEmpty();
    }

    @Test
    @DisplayName("backfillValuations retorna 0 y no consulta la API para activos sin seguimiento automático")
    void backfillValuations_returnsZero_whenNotAutoTracked() {
        InvestmentAsset asset = buildBonoAsset(UUID.randomUUID(), "AL30");
        asset.setAutoTrack(false);

        int created = service.backfillValuations(asset);

        assertThat(created).isZero();
        verify(ppiMarketDataClient, never()).getPriceSeries(any(), any(), any(), any());
    }

    @Test
    @DisplayName("backfillValuations retorna 0 para tipos que no son LETRA/BONO/ON (aislamiento)")
    void backfillValuations_returnsZero_forNonPpiType() {
        InvestmentAsset asset = buildBonoAsset(UUID.randomUUID(), "AL30");
        asset.setType(InvestmentAssetType.FCI_CUOTAPARTES);

        int created = service.backfillValuations(asset);

        assertThat(created).isZero();
        verify(ppiMarketDataClient, never()).getPriceSeries(any(), any(), any(), any());
    }

    // ─── getInstrumentsByType ─────────────────────────────────────────────────

    @Test
    @DisplayName("getInstrumentsByType retorna lista filtrada desde la caché")
    void getInstrumentsByType_returnsCachedList() {
        com.vectis.backend.domain.entity.DoctaInstrumentCache cached =
                com.vectis.backend.domain.entity.DoctaInstrumentCache.builder()
                        .ticker("AL30")
                        .nombre("BONO SOBERANO AL30")
                        .tipo("BONO")
                        .lastPrice(new BigDecimal("1450.5000"))
                        .priceDate(LocalDate.now())
                        .maturityDate(LocalDate.of(2030, 7, 9))
                        .build();

        given(instrumentCacheRepository.findAllByTipoAndActiveTrueOrderByNombreAsc("BONO"))
                .willReturn(List.of(cached));

        List<InstrumentDto> result = service.getInstrumentsByType("BONO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("AL30");
        assertThat(result.get(0).lastPrice()).isEqualByComparingTo("1450.5000");
        assertThat(result.get(0).maturityDate()).isEqualTo(LocalDate.of(2030, 7, 9));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private InvestmentAsset buildBonoAsset(UUID id, String ticker) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@vectis.com")
                .fullName("Test")
                .passwordHash("hash")
                .build();
        return InvestmentAsset.builder()
                .id(id)
                .user(user)
                .name("BONO AL30")
                .type(InvestmentAssetType.BONO)
                .currency("USD")
                .principal(new BigDecimal("1000000.0000"))
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .autoTrack(true)
                .externalId(ticker)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
