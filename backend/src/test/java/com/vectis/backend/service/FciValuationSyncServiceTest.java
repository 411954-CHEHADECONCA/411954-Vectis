package com.vectis.backend.service;

import com.vectis.backend.domain.entity.FciVcpSnapshot;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.FciFundDto;
import com.vectis.backend.dto.macro.ArgentinadatosFciDto;
import com.vectis.backend.repository.FciVcpSnapshotRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FciValuationSyncService")
class FciValuationSyncServiceTest {

    @Mock private RestTemplate                  macroRestTemplate;
    @Mock private FciVcpSnapshotRepository      fciVcpSnapshotRepository;
    @Mock private InvestmentRepository          investmentRepository;
    @Mock private InvestmentValuationRepository valuationRepository;

    private FciValuationSyncService service;

    private static final String MACRO_BASE_URL = "https://api.argentinadatos.com/v1";
    private static final String FONDO_NAME     = "Cocos Capital - Clase A";

    @BeforeEach
    void setUp() {
        service = new FciValuationSyncService(
                macroRestTemplate, fciVcpSnapshotRepository,
                investmentRepository, valuationRepository);
        ReflectionTestUtils.setField(service, "macroBaseUrl", MACRO_BASE_URL);
    }

    // ─── fetchFciVcp ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchFciVcp guarda snapshots nuevos cuando no existen")
    void fetchFciVcp_savesNewSnapshots_whenNotExists() {
        ArgentinadatosFciDto dto = new ArgentinadatosFciDto(
                FONDO_NAME, "Corto Plazo", "2026-06-24", new BigDecimal("1099.3320"));
        ArgentinadatosFciDto[] response = {dto};

        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosFciDto[].class)))
                .willReturn(response);
        given(fciVcpSnapshotRepository.existsByFondoAndFecha(anyString(), any(LocalDate.class)))
                .willReturn(false);

        service.fetchFciVcp();

        // 4 categories × 1 DTO each = 4 save calls
        verify(fciVcpSnapshotRepository, times(4)).save(any(FciVcpSnapshot.class));
    }

    @Test
    @DisplayName("fetchFciVcp consulta la categoría rentaMixta")
    void fetchFciVcp_queriesRentaMixtaCategory() {
        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosFciDto[].class)))
                .willReturn(new ArgentinadatosFciDto[]{});

        service.fetchFciVcp();

        verify(macroRestTemplate).getForObject(
                eq(MACRO_BASE_URL + "/finanzas/fci/rentaMixta/ultimo"),
                eq(ArgentinadatosFciDto[].class));
    }

    @Test
    @DisplayName("getLatestFciFunds retorna los fondos de la categoría rentaMixta")
    void getLatestFciFunds_returnsRentaMixtaFunds() {
        LocalDate snapshotDate = LocalDate.of(2026, 6, 26);
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .fondo("Pionero Multiestrategia Mix - Clase B")
                .categoria("rentaMixta")
                .vcp(new BigDecimal("1694.2850"))
                .fecha(snapshotDate)
                .build();

        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(snapshotDate));
        given(fciVcpSnapshotRepository.findByCategoriaAndFecha("rentaMixta", snapshotDate))
                .willReturn(List.of(snapshot));

        List<FciFundDto> result = service.getLatestFciFunds("rentaMixta");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoria()).isEqualTo("rentaMixta");
        assertThat(result.get(0).fondo()).isEqualTo("Pionero Multiestrategia Mix - Clase B");
    }

    @Test
    @DisplayName("fetchFciVcp omite snapshots que ya existen (idempotencia)")
    void fetchFciVcp_skipsExisting_whenAlreadySaved() {
        ArgentinadatosFciDto dto = new ArgentinadatosFciDto(
                FONDO_NAME, "Corto Plazo", "2026-06-24", new BigDecimal("1099.3320"));
        ArgentinadatosFciDto[] response = {dto};

        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosFciDto[].class)))
                .willReturn(response);
        given(fciVcpSnapshotRepository.existsByFondoAndFecha(anyString(), any(LocalDate.class)))
                .willReturn(true);

        service.fetchFciVcp();

        verify(fciVcpSnapshotRepository, never()).save(any(FciVcpSnapshot.class));
    }

    @Test
    @DisplayName("fetchFciVcp no lanza excepción cuando la API retorna null")
    void fetchFciVcp_handlesNullResponse_gracefully() {
        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosFciDto[].class)))
                .willReturn(null);

        // Should not throw
        service.fetchFciVcp();

        verify(fciVcpSnapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("fetchFciVcp no lanza excepción cuando la API falla (resiliencia)")
    void fetchFciVcp_handlesException_gracefully() {
        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosFciDto[].class)))
                .willThrow(new RuntimeException("timeout"));

        // Should not throw — log warning and continue
        service.fetchFciVcp();

        verify(fciVcpSnapshotRepository, never()).save(any());
    }

    // ─── syncFciValuations ────────────────────────────────────────────────────

    @Test
    @DisplayName("syncFciValuations crea valuación con la fecha del snapshot, no la de hoy (regresión)")
    void syncFciValuations_createsValuationWithSnapshotDate_whenLastSnapshotIsStale() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME); // purchaseDate 2026-01-01

        LocalDate staleDate = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3);
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .id(UUID.randomUUID())
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(staleDate)
                .build();

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));
        given(fciVcpSnapshotRepository.findTopByFondoOrderByFechaDesc(FONDO_NAME))
                .willReturn(Optional.of(snapshot));
        given(valuationRepository.findByInvestmentAsset_IdAndValuationDate(assetId, staleDate))
                .willReturn(Optional.empty());

        service.syncFciValuations();

        ArgumentCaptor<InvestmentValuation> captor = ArgumentCaptor.forClass(InvestmentValuation.class);
        verify(valuationRepository).save(captor.capture());
        InvestmentValuation saved = captor.getValue();
        assertThat(saved.getValuationDate()).isEqualTo(staleDate);
        assertThat(saved.getValuationDate()).isNotEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(saved.getPricePerUnit()).isEqualByComparingTo("1099.3320");
        assertThat(saved.getSource()).isEqualTo("ARGENTINADATOS");
        assertThat(saved.isAutoGenerated()).isTrue();
        assertThat(saved.getInvestmentAsset()).isEqualTo(asset);
    }

    @Test
    @DisplayName("saveFciValuation pisa una valuación auto-generada existente con el precio de mercado")
    void saveFciValuation_overridesAutoGeneratedExisting() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME);
        LocalDate date = LocalDate.of(2026, 7, 23);

        InvestmentValuation existing = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(date)
                .pricePerUnit(new BigDecimal("100.0000"))
                .source("MANUAL")
                .autoGenerated(true)
                .build();

        given(valuationRepository.findByInvestmentAsset_IdAndValuationDate(assetId, date))
                .willReturn(Optional.of(existing));

        service.saveFciValuation(asset, date, new BigDecimal("120.5000"));

        assertThat(existing.getPricePerUnit()).isEqualByComparingTo("120.5000");
        assertThat(existing.getSource()).isEqualTo("ARGENTINADATOS");
        assertThat(existing.isAutoGenerated()).isTrue();
        verify(valuationRepository).save(existing);
        verify(valuationRepository, never()).save(argThat(v -> v != existing));
    }

    @Test
    @DisplayName("saveFciValuation NO pisa una valuación manual (autoGenerated=false) existente")
    void saveFciValuation_doesNotOverrideManualExisting() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME);
        LocalDate date = LocalDate.of(2026, 7, 23);

        InvestmentValuation existing = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(date)
                .pricePerUnit(new BigDecimal("100.0000"))
                .source("MANUAL")
                .autoGenerated(false)
                .build();

        given(valuationRepository.findByInvestmentAsset_IdAndValuationDate(assetId, date))
                .willReturn(Optional.of(existing));

        service.saveFciValuation(asset, date, new BigDecimal("120.5000"));

        assertThat(existing.getPricePerUnit()).isEqualByComparingTo("100.0000");
        assertThat(existing.getSource()).isEqualTo("MANUAL");
        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFciValuations no crea valuación si ya existe para la fecha del snapshot y es manual (idempotencia)")
    void syncFciValuations_skipsAsset_whenValuationAlreadyExistsForSnapshotDate() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME);

        LocalDate snapshotDate = LocalDate.of(2026, 7, 21);
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .id(UUID.randomUUID())
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(snapshotDate)
                .build();

        InvestmentValuation existing = InvestmentValuation.builder()
                .id(UUID.randomUUID())
                .investmentAsset(asset)
                .valuationDate(snapshotDate)
                .pricePerUnit(new BigDecimal("1050.0000"))
                .source("MANUAL")
                .autoGenerated(false)
                .build();

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));
        given(fciVcpSnapshotRepository.findTopByFondoOrderByFechaDesc(FONDO_NAME))
                .willReturn(Optional.of(snapshot));
        given(valuationRepository.findByInvestmentAsset_IdAndValuationDate(assetId, snapshotDate))
                .willReturn(Optional.of(existing));

        service.syncFciValuations();

        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFciValuations no crea valuación si el snapshot es anterior a la compra del activo")
    void syncFciValuations_skipsAsset_whenSnapshotBeforePurchaseDate() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME); // purchaseDate 2026-01-01

        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .id(UUID.randomUUID())
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(LocalDate.of(2025, 12, 31)) // anterior a purchaseDate
                .build();

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));
        given(fciVcpSnapshotRepository.findTopByFondoOrderByFechaDesc(FONDO_NAME))
                .willReturn(Optional.of(snapshot));

        service.syncFciValuations();

        verify(valuationRepository, never()).save(any());
        verify(valuationRepository, never()).findByInvestmentAsset_IdAndValuationDate(any(), any());
    }

    @Test
    @DisplayName("syncFciValuations omite activo cuando externalId es null")
    void syncFciValuations_skipsAsset_whenExternalIdIsNull() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, null); // null externalId

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));

        service.syncFciValuations();

        verify(fciVcpSnapshotRepository, never()).findTopByFondoOrderByFechaDesc(any());
        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFciValuations omite activo cuando externalId está en blanco")
    void syncFciValuations_skipsAsset_whenExternalIdIsBlank() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, "   ");

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));

        service.syncFciValuations();

        verify(fciVcpSnapshotRepository, never()).findTopByFondoOrderByFechaDesc(any());
        verify(valuationRepository, never()).save(any());
    }

    // ─── getVcpForDate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVcpForDate retorna FciFundDto cuando existe snapshot para el fondo y fecha")
    void getVcpForDate_returnsFciFundDto_whenSnapshotExists() {
        LocalDate fecha = LocalDate.of(2026, 6, 25);
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(fecha)
                .build();

        given(fciVcpSnapshotRepository.findTopByFondoAndFechaLessThanEqualOrderByFechaDesc(FONDO_NAME, fecha))
                .willReturn(Optional.of(snapshot));

        Optional<FciFundDto> result = service.getVcpForDate(FONDO_NAME, fecha);

        assertThat(result).isPresent();
        FciFundDto dto = result.get();
        assertThat(dto.fondo()).isEqualTo(FONDO_NAME);
        assertThat(dto.categoria()).isEqualTo("mercadoDinero");
        assertThat(dto.vcp()).isEqualByComparingTo("1099.3320");
        assertThat(dto.fecha()).isEqualTo(fecha);
    }

    @Test
    @DisplayName("getVcpForDate retorna el snapshot más reciente ≤ fecha y no cae al histórico")
    void getVcpForDate_returnsMostRecentSnapshotBeforeDate_withoutFallingBackToHistorico() {
        LocalDate fecha = LocalDate.of(2026, 6, 25);
        LocalDate snapshotDate = LocalDate.of(2026, 6, 23); // no hay snapshot exacto del 25, pero sí uno anterior
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(snapshotDate)
                .build();

        given(fciVcpSnapshotRepository.findTopByFondoAndFechaLessThanEqualOrderByFechaDesc(FONDO_NAME, fecha))
                .willReturn(Optional.of(snapshot));

        Optional<FciFundDto> result = service.getVcpForDate(FONDO_NAME, fecha);

        assertThat(result).isPresent();
        assertThat(result.get().fecha()).isEqualTo(snapshotDate);
        verify(macroRestTemplate, never()).getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class));
    }

    @Test
    @DisplayName("getVcpForDate retorna empty cuando no hay snapshot ni histórico para el fondo y fecha")
    void getVcpForDate_returnsEmpty_whenNoSnapshotNorHistorico() {
        LocalDate fecha = LocalDate.of(2026, 6, 25);

        given(fciVcpSnapshotRepository.findTopByFondoAndFechaLessThanEqualOrderByFechaDesc("fondo-inexistente", fecha))
                .willReturn(Optional.empty());
        given(macroRestTemplate.getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class)))
                .willReturn(null); // histórico tampoco disponible

        Optional<FciFundDto> result = service.getVcpForDate("fondo-inexistente", fecha);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getVcpForDate cae al histórico de argentinadatos cuando no hay snapshot (fecha pasada)")
    void getVcpForDate_fallsBackToHistorico_whenNoSnapshot() {
        LocalDate fecha = LocalDate.of(2026, 3, 3);

        given(fciVcpSnapshotRepository.findTopByFondoAndFechaLessThanEqualOrderByFechaDesc(FONDO_NAME, fecha))
                .willReturn(Optional.empty());
        // Serie histórica: el cierre exacto del 03/03 y uno anterior; debe elegir el ≤ fecha más reciente.
        var response = new com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto(
                FONDO_NAME,
                java.util.List.of(
                    histo("2026-03-02", "1378140.3800"),
                    histo("2026-03-03", "1378257.0290"),
                    histo("2026-03-05", "1380000.0000") // posterior a la fecha → se ignora
                ));
        given(macroRestTemplate.getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class)))
                .willReturn(response);

        Optional<FciFundDto> result = service.getVcpForDate(FONDO_NAME, fecha);

        assertThat(result).isPresent();
        assertThat(result.get().fondo()).isEqualTo(FONDO_NAME);
        assertThat(result.get().vcp()).isEqualByComparingTo("1378257.0290");
        assertThat(result.get().fecha()).isEqualTo(LocalDate.of(2026, 3, 3));
    }

    // ─── getLatestFciFunds ────────────────────────────────────────────────────

    @Test
    @DisplayName("getLatestFciFunds retorna lista mapeada del snapshot más reciente")
    void getLatestFciFunds_returnsMappedList() {
        LocalDate snapshotDate = LocalDate.of(2026, 6, 24);
        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(snapshotDate)
                .build();

        given(fciVcpSnapshotRepository.findLatestSnapshotDate()).willReturn(Optional.of(snapshotDate));
        given(fciVcpSnapshotRepository.findByCategoriaAndFecha("mercadoDinero", snapshotDate))
                .willReturn(List.of(snapshot));

        List<FciFundDto> result = service.getLatestFciFunds("mercadoDinero");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fondo()).isEqualTo(FONDO_NAME);
        assertThat(result.get(0).vcp()).isEqualByComparingTo("1099.3320");
    }

    // ─── backfillValuations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("backfillValuations agrega una valuación por cada día del histórico dentro del rango")
    void backfillValuations_addsValuationsInRange() {
        InvestmentAsset asset = buildFciCuotapartesAsset(UUID.randomUUID(), FONDO_NAME); // purchaseDate 2026-01-01

        var response = new com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto(
                FONDO_NAME,
                java.util.List.of(
                    histo("2025-12-01", "1000.0000"),  // antes de la compra → excluida
                    histo("2026-01-05", "1010.5000"),
                    histo("2026-01-06", "1020.0000"),
                    histo("2999-01-01", "9999.0000")   // futura → excluida
                ));
        given(macroRestTemplate.getForObject(contains("/finanzas/fci/fondos/"),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class)))
                .willReturn(response);

        int created = service.backfillValuations(asset);

        assertThat(created).isEqualTo(2);
        assertThat(asset.getValuations()).hasSize(2);
        assertThat(asset.getValuations()).allMatch(v -> "ARGENTINADATOS".equals(v.getSource()));
        assertThat(asset.getValuations()).anyMatch(v ->
                v.getValuationDate().equals(LocalDate.of(2026, 1, 5))
                        && v.getPricePerUnit().compareTo(new BigDecimal("1010.5000")) == 0);
    }

    @Test
    @DisplayName("backfillValuations no duplica valuaciones de fechas ya existentes")
    void backfillValuations_skipsExistingDates() {
        InvestmentAsset asset = buildFciCuotapartesAsset(UUID.randomUUID(), FONDO_NAME);
        asset.getValuations().add(InvestmentValuation.builder()
                .investmentAsset(asset)
                .valuationDate(LocalDate.of(2026, 1, 5))
                .pricePerUnit(new BigDecimal("999.0000"))
                .source("MANUAL")
                .build());

        var response = new com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto(
                FONDO_NAME,
                java.util.List.of(
                    histo("2026-01-05", "1010.5000"),  // ya existe → no se duplica
                    histo("2026-01-06", "1020.0000")
                ));
        given(macroRestTemplate.getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class)))
                .willReturn(response);

        int created = service.backfillValuations(asset);

        assertThat(created).isEqualTo(1);
        assertThat(asset.getValuations()).hasSize(2);
        // la valuación existente sigue siendo MANUAL (no se sobrescribe)
        assertThat(asset.getValuations()).anyMatch(v ->
                v.getValuationDate().equals(LocalDate.of(2026, 1, 5)) && "MANUAL".equals(v.getSource()));
    }

    @Test
    @DisplayName("backfillValuations retorna 0 y no llama a la API si el activo no es FCI auto-trackeado")
    void backfillValuations_returnsZero_whenNotAutoTrackedFci() {
        InvestmentAsset asset = buildFciCuotapartesAsset(UUID.randomUUID(), "  "); // externalId en blanco

        int created = service.backfillValuations(asset);

        assertThat(created).isZero();
        verify(macroRestTemplate, never()).getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class));
    }

    @Test
    @DisplayName("backfillValuations no lanza y retorna 0 cuando la API falla")
    void backfillValuations_handlesApiFailure_gracefully() {
        InvestmentAsset asset = buildFciCuotapartesAsset(UUID.randomUUID(), FONDO_NAME);
        given(macroRestTemplate.getForObject(anyString(),
                eq(com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoResponseDto.class)))
                .willThrow(new RuntimeException("timeout"));

        int created = service.backfillValuations(asset);

        assertThat(created).isZero();
        assertThat(asset.getValuations()).isEmpty();
    }

    @Test
    @DisplayName("slugify normaliza nombre de fondo a minúsculas con guiones y sin acentos")
    void slugify_normalizesFundName() {
        assertThat(FciValuationSyncService.slugify("Alpha Pesos - Clase A")).isEqualTo("alpha-pesos-clase-a");
        assertThat(FciValuationSyncService.slugify("Ñandú Capital  Mix")).isEqualTo("nandu-capital-mix");
    }

    private static com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoDto histo(String fecha, String vcp) {
        return new com.vectis.backend.dto.macro.ArgentinadatosFciHistoricoDto(
                FONDO_NAME, fecha, new BigDecimal(vcp), "mercadoDinero");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private InvestmentAsset buildFciCuotapartesAsset(UUID id, String externalId) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@vectis.com")
                .fullName("Test")
                .passwordHash("hash")
                .build();
        return InvestmentAsset.builder()
                .id(id)
                .user(user)
                .name("FCI Cuotapartes Test")
                .type(InvestmentAssetType.FCI_CUOTAPARTES)
                .currency("ARS")
                .principal(BigDecimal.ZERO)
                .purchaseDate(LocalDate.of(2026, 1, 1))
                .tna(BigDecimal.ZERO)
                .autoTrack(true)
                .externalId(externalId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
