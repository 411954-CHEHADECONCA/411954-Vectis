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
    @DisplayName("syncFciValuations crea valuación cuando hay VCP del día y no existe valuación")
    void syncFciValuations_createsValuation_whenVcpExistsAndNoValuationToday() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME);

        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .id(UUID.randomUUID())
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(LocalDate.now())
                .build();

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));
        given(fciVcpSnapshotRepository.findByFondoAndFecha(eq(FONDO_NAME), any(LocalDate.class)))
                .willReturn(Optional.of(snapshot));
        given(valuationRepository.existsByInvestmentAsset_IdAndValuationDate(eq(assetId), any(LocalDate.class)))
                .willReturn(false);

        service.syncFciValuations();

        ArgumentCaptor<InvestmentValuation> captor = ArgumentCaptor.forClass(InvestmentValuation.class);
        verify(valuationRepository).save(captor.capture());
        InvestmentValuation saved = captor.getValue();
        assertThat(saved.getPricePerUnit()).isEqualByComparingTo("1099.3320");
        assertThat(saved.getSource()).isEqualTo("ARGENTINADATOS");
        assertThat(saved.getInvestmentAsset()).isEqualTo(asset);
    }

    @Test
    @DisplayName("syncFciValuations no crea valuación si ya existe para el día (idempotencia)")
    void syncFciValuations_skipsAsset_whenValuationAlreadyExistsToday() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, FONDO_NAME);

        FciVcpSnapshot snapshot = FciVcpSnapshot.builder()
                .id(UUID.randomUUID())
                .fondo(FONDO_NAME)
                .categoria("mercadoDinero")
                .vcp(new BigDecimal("1099.3320"))
                .fecha(LocalDate.now())
                .build();

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));
        given(fciVcpSnapshotRepository.findByFondoAndFecha(eq(FONDO_NAME), any(LocalDate.class)))
                .willReturn(Optional.of(snapshot));
        given(valuationRepository.existsByInvestmentAsset_IdAndValuationDate(eq(assetId), any(LocalDate.class)))
                .willReturn(true);

        service.syncFciValuations();

        verify(valuationRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFciValuations omite activo cuando externalId es null")
    void syncFciValuations_skipsAsset_whenExternalIdIsNull() {
        UUID assetId = UUID.randomUUID();
        InvestmentAsset asset = buildFciCuotapartesAsset(assetId, null); // null externalId

        given(investmentRepository.findAllByTypeAndAutoTrackTrue(InvestmentAssetType.FCI_CUOTAPARTES))
                .willReturn(List.of(asset));

        service.syncFciValuations();

        verify(fciVcpSnapshotRepository, never()).findByFondoAndFecha(any(), any());
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

        verify(fciVcpSnapshotRepository, never()).findByFondoAndFecha(any(), any());
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

        given(fciVcpSnapshotRepository.findByFondoAndFecha(FONDO_NAME, fecha))
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
    @DisplayName("getVcpForDate retorna empty cuando no hay snapshot ni histórico para el fondo y fecha")
    void getVcpForDate_returnsEmpty_whenNoSnapshotNorHistorico() {
        LocalDate fecha = LocalDate.of(2026, 6, 25);

        given(fciVcpSnapshotRepository.findByFondoAndFecha("fondo-inexistente", fecha))
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

        given(fciVcpSnapshotRepository.findByFondoAndFecha(FONDO_NAME, fecha))
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
