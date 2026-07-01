package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient;
import com.vectis.backend.config.PpiMarketDataClient.PpiInstrument;
import com.vectis.backend.domain.entity.DoctaInstrumentCache;
import com.vectis.backend.repository.DoctaInstrumentCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InstrumentCatalogSyncService")
class InstrumentCatalogSyncServiceTest {

    @InjectMocks
    private InstrumentCatalogSyncService service;

    @Mock private PpiMarketDataClient            ppiMarketDataClient;
    @Mock private DoctaInstrumentCacheRepository instrumentCacheRepository;

    private static DoctaInstrumentCache letra(String ticker) {
        return DoctaInstrumentCache.builder().ticker(ticker).nombre(ticker).tipo("LETRA").active(true).build();
    }

    /** Una LECAP válida de PPI (tipo LETRAS, capitalizable, pesos). */
    private static PpiInstrument ppiLecap(String ticker, String fechaVto) {
        return new PpiInstrument(ticker, "LETRA TESORO NACIONAL CAPITALIZABLE " + fechaVto + " $", "LETRAS", "Pesos");
    }

    @Test
    @DisplayName("syncCatalog no hace nada cuando PPI no está configurado")
    void syncCatalog_skipsAll_whenNotConfigured() {
        given(ppiMarketDataClient.isConfigured()).willReturn(false);

        service.syncCatalog();

        verify(ppiMarketDataClient, never()).searchInstruments(anyString(), anyString());
        verify(instrumentCacheRepository, never()).findAllByTipoInAndActiveTrue(any());
    }

    @Test
    @DisplayName("pruning: da de baja las LETRA del catálogo que ya no están en el set vigente de PPI")
    void syncCatalog_deactivatesDelistedTicker() {
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        // PPI devuelve S31L6 como vigente; S18D6 no aparece → debe darse de baja.
        given(ppiMarketDataClient.searchInstruments(anyString(), anyString()))
                .willReturn(List.of(ppiLecap("S31L6", "31/07/26")));
        given(instrumentCacheRepository.findAllByTipoInAndActiveTrue(List.of("LETRA")))
                .willReturn(List.of(letra("S18D6"), letra("S31L6")));
        given(instrumentCacheRepository.findByTicker("S31L6")).willReturn(Optional.of(letra("S31L6")));

        service.syncCatalog();

        // S18D6 se da de baja (active=false).
        ArgumentCaptor<DoctaInstrumentCache> captor = ArgumentCaptor.forClass(DoctaInstrumentCache.class);
        verify(instrumentCacheRepository, atLeastOnce()).save(captor.capture());
        DoctaInstrumentCache baja = captor.getAllValues().stream()
                .filter(c -> "S18D6".equals(c.getTicker())).findFirst().orElseThrow();
        assertThat(baja.isActive()).isFalse();
    }

    @Test
    @DisplayName("seguridad: si PPI no devuelve LECAPs (fallo transitorio) no da de baja nada")
    void syncCatalog_skipsPruning_whenDiscoveryEmpty() {
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.searchInstruments(anyString(), anyString())).willReturn(List.of());

        service.syncCatalog();

        // No se consulta el catálogo ni se desactiva nada: evita falsos positivos por error de red.
        verify(instrumentCacheRepository, never()).findAllByTipoInAndActiveTrue(any());
        verify(instrumentCacheRepository, never()).save(any());
    }

    @Test
    @DisplayName("descubrimiento: hace upsert (alta) de una LECAP nueva con vencimiento parseado e ignora no-LECAPs")
    void syncCatalog_upsertsNewLecap() {
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(instrumentCacheRepository.findAllByTipoInAndActiveTrue(any())).willReturn(List.of());
        given(ppiMarketDataClient.searchInstruments(anyString(), anyString())).willReturn(List.of(
                ppiLecap("S30N6", "30/11/26"),
                // tipo BONOS → se ignora (evita clasificar un BONCAP como LETRA)
                new PpiInstrument("XYZ", "BONO TESORO NACIONAL CAPITALIZABLE 30/06/35 $", "BONOS", "Pesos"),
                // dólar-linked → se ignora (no es en pesos)
                new PpiInstrument("DM7C", "LETRA CAPITALIZABLE 31/07/27 USD", "LETRAS", "Dolares divisa")));
        given(instrumentCacheRepository.findByTicker("S30N6")).willReturn(Optional.empty());

        service.syncCatalog();

        ArgumentCaptor<DoctaInstrumentCache> captor = ArgumentCaptor.forClass(DoctaInstrumentCache.class);
        verify(instrumentCacheRepository).save(captor.capture());
        DoctaInstrumentCache saved = captor.getValue();
        assertThat(saved.getTicker()).isEqualTo("S30N6");
        assertThat(saved.getTipo()).isEqualTo("LETRA");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getMaturityDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("descubrimiento: reactiva y actualiza un instrumento existente que estaba inactivo")
    void syncCatalog_reactivatesExistingInactive() {
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(instrumentCacheRepository.findAllByTipoInAndActiveTrue(any())).willReturn(List.of());
        given(ppiMarketDataClient.searchInstruments(anyString(), anyString()))
                .willReturn(List.of(ppiLecap("S30N6", "30/11/26")));
        DoctaInstrumentCache inactive = DoctaInstrumentCache.builder()
                .ticker("S30N6").nombre("viejo").tipo("LETRA").active(false).build();
        given(instrumentCacheRepository.findByTicker("S30N6")).willReturn(Optional.of(inactive));

        service.syncCatalog();

        verify(instrumentCacheRepository).save(inactive);
        assertThat(inactive.isActive()).isTrue();
        assertThat(inactive.getNombre()).isEqualTo("LETRA TESORO NACIONAL CAPITALIZABLE 30/11/26 $");
        assertThat(inactive.getMaturityDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("resiliencia: una excepción dando de baja un instrumento no aborta el resto")
    void syncCatalog_resilientToPerItemFailure() {
        given(ppiMarketDataClient.isConfigured()).willReturn(true);
        given(ppiMarketDataClient.searchInstruments(anyString(), anyString()))
                .willReturn(List.of(ppiLecap("S31L6", "31/07/26")));
        DoctaInstrumentCache boom = letra("BOOM");
        DoctaInstrumentCache s18 = letra("S18D6");
        given(instrumentCacheRepository.findAllByTipoInAndActiveTrue(List.of("LETRA")))
                .willReturn(List.of(boom, s18));
        given(instrumentCacheRepository.findByTicker("S31L6")).willReturn(Optional.of(letra("S31L6")));
        // El save del primer delisted (BOOM) falla; el segundo (S18D6) igual debe procesarse.
        given(instrumentCacheRepository.save(boom)).willThrow(new RuntimeException("db down"));

        service.syncCatalog(); // no lanza

        verify(instrumentCacheRepository).save(s18);
        assertThat(s18.isActive()).isFalse();
    }

    @Test
    @DisplayName("parseMaturity extrae DD/MM/YY de la descripción y tolera formatos inválidos")
    void parseMaturity_parsesAndTolerates() {
        assertThat(InstrumentCatalogSyncService.parseMaturity("LETRA ... CAPITALIZABLE 30/11/26 $"))
                .isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(InstrumentCatalogSyncService.parseMaturity("sin fecha")).isNull();
        assertThat(InstrumentCatalogSyncService.parseMaturity(null)).isNull();
        // Año de 2 dígitos con pivote base-2000 (35 → 2035, no 1935) para BONCAPs largos.
        assertThat(InstrumentCatalogSyncService.parseMaturity("BONO CAPITALIZABLE 30/06/35 $"))
                .isEqualTo(LocalDate.of(2035, 6, 30));
        // Con múltiples fechas toma la primera (convención: la de vencimiento va primero).
        assertThat(InstrumentCatalogSyncService.parseMaturity("emitida 01/02/24 vto 30/11/26"))
                .isEqualTo(LocalDate.of(2024, 2, 1));
    }
}
