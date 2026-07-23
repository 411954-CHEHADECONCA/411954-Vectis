package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.dto.MarketRefreshResponse;
import com.vectis.backend.dto.MarketSourceRefreshResult;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.FciVcpSnapshotRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataRefreshService")
class MarketDataRefreshServiceTest {

    @Mock private MacroDataService macroDataService;
    @Mock private FciValuationSyncService fciValuationSyncService;
    @Mock private PpiValuationSyncService ppiValuationSyncService;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private FciVcpSnapshotRepository fciVcpSnapshotRepository;
    @Mock private InvestmentValuationRepository investmentValuationRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private PpiMarketDataClient ppiMarketDataClient;

    @InjectMocks
    private MarketDataRefreshService service;

    private LocalDate friday;

    @BeforeEach
    void setUp() {
        // Un viernes fijo: sirve tanto como "hoy laborable" como último día hábil de un sábado/domingo.
        friday = LocalDate.of(2026, 7, 3);
        assertThat(friday.getDayOfWeek().getValue()).isEqualTo(5);
    }

    // El servicio calcula la frescura contra lastBusinessDay(LocalDate.now(ZoneOffset.UTC)); los
    // mocks de "última fecha disponible" deben usar el MISMO reloj UTC, o de noche en Argentina
    // (UTC-3, cuando la fecha UTC ya avanzó un día) FCI se compararía como desactualizado.
    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    // ─── lastBusinessDay ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "2026-07-06,2026-07-06", // lunes -> lunes
        "2026-07-03,2026-07-03", // viernes -> viernes
        "2026-07-04,2026-07-03", // sabado -> viernes
        "2026-07-05,2026-07-03"  // domingo -> viernes
    })
    @DisplayName("lastBusinessDay resuelve fines de semana al viernes anterior")
    void lastBusinessDay_resolvesWeekends(String reference, String expected) {
        LocalDate result = MarketDataRefreshService.lastBusinessDay(LocalDate.parse(reference));
        assertThat(result).isEqualTo(LocalDate.parse(expected));
    }

    // ─── force=false, todo al día ────────────────────────────────────────────

    @Test
    @DisplayName("force=false y todas las fuentes frescas: no invoca ningún sync externo, todas upToDate")
    void refresh_notForced_allFresh_skipsSyncCalls() {
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(true);
        given(macroDataService.getLatestMepDate()).willReturn(today());
        given(macroDataService.getLatestOficialDate()).willReturn(today());
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(anyList()))
                .willReturn(List.of(new InvestmentAsset()));
        given(investmentValuationRepository.existsBySourceAndValuationDate(anyString(), any())).willReturn(true);
        given(ppiValuationSyncService.getLastSyncDate()).willReturn(today());

        MarketRefreshResponse response = service.refresh(false);

        assertThat(response.sources()).hasSize(4);
        assertThat(response.sources())
                .allSatisfy(r -> assertThat(r.status()).isEqualTo("upToDate"));

        verify(macroDataService, never()).fetchLatestMepRate();
        verify(macroDataService, never()).fetchOficialRate();
        verify(fciValuationSyncService, never()).fetchFciVcp();
        verify(ppiValuationSyncService, never()).syncPpiValuations();
    }

    // ─── force=true, invoca todo ─────────────────────────────────────────────

    @Test
    @DisplayName("force=true invoca el sync de las cuatro fuentes aunque estén frescas")
    void refresh_forced_invokesAllSyncsRegardlessOfFreshness() {
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(true);
        given(macroDataService.getLatestMepDate()).willReturn(today());
        given(macroDataService.getLatestOficialDate()).willReturn(today());
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(anyList()))
                .willReturn(List.of(new InvestmentAsset()));
        given(investmentValuationRepository.existsBySourceAndValuationDate(anyString(), any())).willReturn(true);
        given(ppiValuationSyncService.getLastSyncDate()).willReturn(today());

        MarketRefreshResponse response = service.refresh(true);

        assertThat(response.sources())
                .allSatisfy(r -> assertThat(r.status()).isEqualTo("refreshed"));

        verify(macroDataService, times(1)).fetchLatestMepRate();
        verify(macroDataService, times(1)).fetchOficialRate();
        verify(fciValuationSyncService, times(1)).fetchFciVcp();
        verify(fciValuationSyncService, times(1)).syncFciValuations();
        verify(ppiValuationSyncService, times(1)).syncPpiValuations();
    }

    // ─── Fallo de una fuente no aborta las demás ─────────────────────────────

    @Test
    @DisplayName("Si MEP falla (sigue no fresco tras el sync), las demás fuentes se procesan igual")
    void refresh_mepFails_othersStillProcessed() {
        // MEP: no fresco antes ni después -> failed
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(false);
        given(macroDataService.getLatestMepDate()).willReturn(null);
        given(macroDataService.getLatestOficialDate()).willReturn(today());

        // FCI y PPI: frescos de entrada (no forzado) -> upToDate, no deberían verse afectados por el fallo de MEP
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(anyList()))
                .willReturn(List.of(new InvestmentAsset()));
        given(ppiValuationSyncService.getLastSyncDate()).willReturn(today());

        MarketRefreshResponse response = service.refresh(false);

        MarketSourceRefreshResult mep = findSource(response, "mep");
        assertThat(mep.status()).isEqualTo("failed");
        assertThat(mep.lastUpdate()).isNull();

        // El resto de las fuentes se siguieron evaluando (no se abortó el flujo completo)
        assertThat(response.sources()).hasSize(4);
        verify(macroDataService, times(1)).fetchLatestMepRate();
        verify(macroDataService, times(1)).fetchOficialRate();
    }

    // ─── PPI no configurado ───────────────────────────────────────────────────

    @Test
    @DisplayName("PPI sin credenciales configuradas: notConfigured, lastUpdate null, no invoca sync ni consulta activos")
    void refresh_ppiNotConfigured_returnsNotConfigured() {
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(true);
        given(macroDataService.getLatestMepDate()).willReturn(today());
        given(macroDataService.getLatestOficialDate()).willReturn(today());
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(false);

        MarketRefreshResponse response = service.refresh(false);

        MarketSourceRefreshResult ppi = findSource(response, "ppi");
        assertThat(ppi.status()).isEqualTo("notConfigured");
        assertThat(ppi.lastUpdate()).isNull();

        verify(ppiValuationSyncService, never()).syncPpiValuations();
        verify(investmentRepository, never()).findAllByTypesAndAutoTrackTrue(anyList());
    }

    @Test
    @DisplayName("PPI configurado pero sin activos auto-track: upToDate sin invocar sync")
    void refresh_ppiConfiguredNoAssets_upToDateWithoutSync() {
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(true);
        given(macroDataService.getLatestMepDate()).willReturn(today());
        given(macroDataService.getLatestOficialDate()).willReturn(today());
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(anyList())).willReturn(List.of());
        given(ppiValuationSyncService.getLastSyncDate()).willReturn(null);

        MarketRefreshResponse response = service.refresh(false);

        MarketSourceRefreshResult ppi = findSource(response, "ppi");
        assertThat(ppi.status()).isEqualTo("upToDate");
        assertThat(ppi.lastUpdate()).isNull();

        verify(ppiValuationSyncService, never()).syncPpiValuations();
    }

    // ─── Exclusión mutua ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Un refresh iniciado mientras otro está en curso devuelve alreadyRunning sin sincronizar; al terminar, el guard se libera")
    void refresh_whileInProgress_returnsAlreadyRunning() {
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(true);
        given(macroDataService.getLatestMepDate()).willReturn(today());
        given(macroDataService.getLatestOficialDate()).willReturn(today());
        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(today()));
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(investmentRepository.findAllByTypesAndAutoTrackTrue(anyList()))
                .willReturn(List.of(new InvestmentAsset()));
        given(investmentValuationRepository.existsBySourceAndValuationDate(anyString(), any())).willReturn(true);
        given(ppiValuationSyncService.getLastSyncDate()).willReturn(today());

        // Simula un segundo request llegando en pleno refresh: el primer sync invocado
        // dispara un refresh reentrante, que debe rebotar contra el guard.
        AtomicReference<MarketRefreshResponse> concurrent = new AtomicReference<>();
        willAnswer(inv -> {
            concurrent.set(service.refresh(true));
            return null;
        }).given(macroDataService).fetchLatestMepRate();

        MarketRefreshResponse outer = service.refresh(true);

        assertThat(concurrent.get()).isNotNull();
        assertThat(concurrent.get().sources())
                .hasSize(4)
                .allSatisfy(r -> assertThat(r.status()).isEqualTo("alreadyRunning"));
        // El request concurrente no volvió a invocar los syncs
        verify(macroDataService, times(1)).fetchLatestMepRate();
        verify(ppiValuationSyncService, times(1)).syncPpiValuations();
        // El refresh original completó con normalidad
        assertThat(outer.sources()).allSatisfy(r -> assertThat(r.status()).isEqualTo("refreshed"));

        // Terminado el primero, el guard queda liberado y un refresh nuevo vuelve a operar
        MarketRefreshResponse after = service.refresh(false);
        assertThat(after.sources()).allSatisfy(r -> assertThat(r.status()).isEqualTo("upToDate"));
    }

    private static MarketSourceRefreshResult findSource(MarketRefreshResponse response, String source) {
        return response.sources().stream()
                .filter(r -> r.source().equals(source))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la fuente: " + source));
    }
}
