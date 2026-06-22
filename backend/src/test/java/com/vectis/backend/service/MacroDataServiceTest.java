package com.vectis.backend.service;

import com.vectis.backend.domain.entity.ExchangeRate;
import com.vectis.backend.domain.entity.InflationRecord;
import com.vectis.backend.dto.ExchangeRateResponse;
import com.vectis.backend.dto.InflationResponse;
import com.vectis.backend.dto.macro.ArgentinadatosInflacionDto;
import com.vectis.backend.dto.macro.ArgentinadatosRateDto;
import com.vectis.backend.dto.macro.DolarApiRateDto;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.ExchangeRateRepository;
import com.vectis.backend.repository.InflationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MacroDataService")
class MacroDataServiceTest {

    @Mock private RestTemplate macroRestTemplate;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private InflationRecordRepository inflationRecordRepository;

    @InjectMocks private MacroDataService macroDataService;

    private static final String MACRO_BASE_URL = "https://api.argentinadatos.com/v1";
    private static final String DOLAR_BASE_URL  = "https://dolarapi.com/v1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(macroDataService, "macroBaseUrl", MACRO_BASE_URL);
        ReflectionTestUtils.setField(macroDataService, "dolarBaseUrl",  DOLAR_BASE_URL);
    }

    // ─── backfillMepRates ────────────────────────────────────────────────────────

    @Test
    @DisplayName("backfillMepRates guarda solo registros >= 2026-01-01")
    void backfillMepRates_savesOnly2026AndNewer() {
        ArgentinadatosRateDto old  = new ArgentinadatosRateDto("bolsa", new BigDecimal("900.00"), new BigDecimal("902.00"), "2025-12-31");
        ArgentinadatosRateDto jan  = new ArgentinadatosRateDto("bolsa", new BigDecimal("1100.00"), new BigDecimal("1102.00"), "2026-01-02");
        ArgentinadatosRateDto jun  = new ArgentinadatosRateDto("bolsa", new BigDecimal("1200.00"), new BigDecimal("1202.00"), "2026-06-01");

        given(macroRestTemplate.getForObject(contains("/cotizaciones/dolares/bolsa"), eq(ArgentinadatosRateDto[].class)))
            .willReturn(new ArgentinadatosRateDto[]{old, jan, jun});
        given(exchangeRateRepository.existsByRateTypeAndRateDate(anyString(), any())).willReturn(false);

        macroDataService.backfillMepRates();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository, times(2)).save(captor.capture());
        List<ExchangeRate> saved = captor.getAllValues();
        assertThat(saved).extracting(ExchangeRate::getRateDate)
            .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("backfillMepRates omite fechas que ya existen en DB")
    void backfillMepRates_skipsExistingDates() {
        ArgentinadatosRateDto dto = new ArgentinadatosRateDto("bolsa", new BigDecimal("1200.00"), new BigDecimal("1202.00"), "2026-06-01");
        given(macroRestTemplate.getForObject(contains("/cotizaciones/dolares/bolsa"), eq(ArgentinadatosRateDto[].class)))
            .willReturn(new ArgentinadatosRateDto[]{dto});
        given(exchangeRateRepository.existsByRateTypeAndRateDate("MEP", LocalDate.of(2026, 6, 1))).willReturn(true);

        macroDataService.backfillMepRates();

        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("backfillInflationRecords guarda solo registros >= 2026-01-01")
    void backfillInflationRecords_savesOnly2026AndNewer() {
        ArgentinadatosInflacionDto old = new ArgentinadatosInflacionDto("2025-12-31", new BigDecimal("2.5"));
        ArgentinadatosInflacionDto rec = new ArgentinadatosInflacionDto("2026-01-31", new BigDecimal("3.1"));

        given(macroRestTemplate.getForObject(contains("/finanzas/indices/inflacion"), eq(ArgentinadatosInflacionDto[].class)))
            .willReturn(new ArgentinadatosInflacionDto[]{old, rec});
        given(inflationRecordRepository.existsByPeriodDate(any())).willReturn(false);

        macroDataService.backfillInflationRecords();

        ArgumentCaptor<InflationRecord> captor = ArgumentCaptor.forClass(InflationRecord.class);
        verify(inflationRecordRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPeriodDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    // ─── fetchLatestMepRate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestMepRate guarda el ultimo entry con valores correctos")
    void fetchLatestMepRate_savesLatestEntry() {
        ArgentinadatosRateDto first  = new ArgentinadatosRateDto("bolsa", new BigDecimal("1100.00"), new BigDecimal("1102.00"), "2026-06-20");
        ArgentinadatosRateDto latest = new ArgentinadatosRateDto("bolsa", new BigDecimal("1200.12"), new BigDecimal("1202.34"), "2026-06-22");

        given(macroRestTemplate.getForObject(contains("/cotizaciones/dolares/bolsa"), eq(ArgentinadatosRateDto[].class)))
            .willReturn(new ArgentinadatosRateDto[]{first, latest});
        given(exchangeRateRepository.existsByRateTypeAndRateDate("MEP", LocalDate.of(2026, 6, 22))).willReturn(false);

        macroDataService.fetchLatestMepRate();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        ExchangeRate saved = captor.getValue();
        assertThat(saved.getRateType()).isEqualTo("MEP");
        assertThat(saved.getRateDate()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(saved.getBuy()).isEqualByComparingTo("1200.1200");
        assertThat(saved.getSell()).isEqualByComparingTo("1202.3400");
    }

    @Test
    @DisplayName("fetchLatestMepRate omite si la fecha ya existe en DB")
    void fetchLatestMepRate_skipsIfAlreadyExists() {
        ArgentinadatosRateDto dto = new ArgentinadatosRateDto("bolsa", new BigDecimal("1200.00"), new BigDecimal("1202.00"), "2026-06-22");
        given(macroRestTemplate.getForObject(contains("/cotizaciones/dolares/bolsa"), eq(ArgentinadatosRateDto[].class)))
            .willReturn(new ArgentinadatosRateDto[]{dto});
        given(exchangeRateRepository.existsByRateTypeAndRateDate("MEP", LocalDate.of(2026, 6, 22))).willReturn(true);

        macroDataService.fetchLatestMepRate();

        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("fetchLatestMepRate no lanza excepcion si la API falla")
    void fetchLatestMepRate_doesNotThrowOnException() {
        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosRateDto[].class)))
            .willThrow(new ResourceAccessException("timeout"));

        macroDataService.fetchLatestMepRate();

        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("fetchLatestMepRate no lanza excepcion si la API retorna array vacio")
    void fetchLatestMepRate_doesNotThrowOnEmptyResponse() {
        given(macroRestTemplate.getForObject(anyString(), eq(ArgentinadatosRateDto[].class)))
            .willReturn(new ArgentinadatosRateDto[]{});

        macroDataService.fetchLatestMepRate();

        verify(exchangeRateRepository, never()).save(any());
    }

    // ─── fetchOficialRate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchOficialRate inserta registro OFICIAL cuando no existe hoy")
    void fetchOficialRate_insertsWhenNotExists() {
        DolarApiRateDto oficial = new DolarApiRateDto("oficial", "Oficial",
                new BigDecimal("1060.00"), new BigDecimal("1062.50"), "2026-06-22T13:00:00.000Z");
        DolarApiRateDto blue    = new DolarApiRateDto("blue", "Blue",
                new BigDecimal("1200.00"), new BigDecimal("1210.00"), "2026-06-22T13:00:00.000Z");

        given(macroRestTemplate.getForObject(contains("/dolares"), eq(DolarApiRateDto[].class)))
            .willReturn(new DolarApiRateDto[]{oficial, blue});
        given(exchangeRateRepository.findByRateTypeAndRateDate(eq("OFICIAL"), any()))
            .willReturn(Optional.empty());

        macroDataService.fetchOficialRate();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        ExchangeRate saved = captor.getValue();
        assertThat(saved.getRateType()).isEqualTo("OFICIAL");
        assertThat(saved.getBuy()).isEqualByComparingTo("1060.0000");
        assertThat(saved.getSell()).isEqualByComparingTo("1062.5000");
    }

    @Test
    @DisplayName("fetchOficialRate actualiza registro existente del dia con nuevo precio")
    void fetchOficialRate_updatesExistingRecord() {
        DolarApiRateDto oficial = new DolarApiRateDto("oficial", "Oficial",
                new BigDecimal("1065.00"), new BigDecimal("1067.50"), "2026-06-22T15:00:00.000Z");

        given(macroRestTemplate.getForObject(contains("/dolares"), eq(DolarApiRateDto[].class)))
            .willReturn(new DolarApiRateDto[]{oficial});

        ExchangeRate existing = ExchangeRate.builder()
                .id(UUID.randomUUID()).rateType("OFICIAL")
                .buy(new BigDecimal("1060.0000")).sell(new BigDecimal("1062.5000"))
                .rateDate(LocalDate.now(ZoneOffset.UTC))
                .build();
        given(exchangeRateRepository.findByRateTypeAndRateDate(eq("OFICIAL"), any()))
            .willReturn(Optional.of(existing));

        macroDataService.fetchOficialRate();

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        assertThat(captor.getValue().getSell()).isEqualByComparingTo("1067.5000");
    }

    @Test
    @DisplayName("fetchOficialRate no lanza excepcion si la API falla")
    void fetchOficialRate_doesNotThrowOnException() {
        given(macroRestTemplate.getForObject(anyString(), eq(DolarApiRateDto[].class)))
            .willThrow(new ResourceAccessException("timeout"));

        macroDataService.fetchOficialRate();

        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("fetchOficialRate ignora la respuesta si no hay casa oficial")
    void fetchOficialRate_ignoresIfNoOficialEntry() {
        DolarApiRateDto blue = new DolarApiRateDto("blue", "Blue",
                new BigDecimal("1200.00"), new BigDecimal("1210.00"), "2026-06-22T13:00:00.000Z");

        given(macroRestTemplate.getForObject(contains("/dolares"), eq(DolarApiRateDto[].class)))
            .willReturn(new DolarApiRateDto[]{blue});

        macroDataService.fetchOficialRate();

        verify(exchangeRateRepository, never()).save(any());
    }

    // ─── fetchLatestInflationIndex ───────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestInflationIndex guarda nuevos registros y omite existentes")
    void fetchLatestInflationIndex_savesNewSkipsExisting() {
        ArgentinadatosInflacionDto existing = new ArgentinadatosInflacionDto("2026-04-30", new BigDecimal("3.7"));
        ArgentinadatosInflacionDto newRec   = new ArgentinadatosInflacionDto("2026-05-31", new BigDecimal("2.4"));

        given(macroRestTemplate.getForObject(contains("/finanzas/indices/inflacion"), eq(ArgentinadatosInflacionDto[].class)))
            .willReturn(new ArgentinadatosInflacionDto[]{existing, newRec});
        given(inflationRecordRepository.existsByPeriodDate(LocalDate.of(2026, 4, 30))).willReturn(true);
        given(inflationRecordRepository.existsByPeriodDate(LocalDate.of(2026, 5, 31))).willReturn(false);

        macroDataService.fetchLatestInflationIndex();

        ArgumentCaptor<InflationRecord> captor = ArgumentCaptor.forClass(InflationRecord.class);
        verify(inflationRecordRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPeriodDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(captor.getValue().getMonthlyRate()).isEqualByComparingTo("2.4000");
    }

    // ─── getLatestOficialRate ────────────────────────────────────────────────────

    @Test
    @DisplayName("getLatestOficialRate retorna ExchangeRateResponse con source dolarapi.com")
    void getLatestOficialRate_returnsResponseWhenExists() {
        ExchangeRate entity = ExchangeRate.builder()
            .id(UUID.randomUUID()).rateType("OFICIAL")
            .buy(new BigDecimal("1060.0000")).sell(new BigDecimal("1062.5000"))
            .rateDate(LocalDate.of(2026, 6, 22)).source("dolarapi.com")
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("OFICIAL"))
            .willReturn(Optional.of(entity));

        ExchangeRateResponse response = macroDataService.getLatestOficialRate();

        assertThat(response.rateType()).isEqualTo("OFICIAL");
        assertThat(response.sell()).isEqualTo("1062.5000");
        assertThat(response.rateDate()).isEqualTo("2026-06-22");
        assertThat(response.source()).isEqualTo("dolarapi.com");
    }

    @Test
    @DisplayName("getLatestOficialRate lanza VectisException 404 cuando la DB esta vacia")
    void getLatestOficialRate_throwsWhenEmpty() {
        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("OFICIAL"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> macroDataService.getLatestOficialRate())
            .isInstanceOf(VectisException.class)
            .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── getLatestMepRate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLatestMepRate retorna ExchangeRateResponse con Strings exactos")
    void getLatestMepRate_returnsResponseWhenExists() {
        ExchangeRate entity = ExchangeRate.builder()
            .id(UUID.randomUUID()).rateType("MEP")
            .buy(new BigDecimal("1200.0000")).sell(new BigDecimal("1202.0000"))
            .rateDate(LocalDate.of(2026, 6, 22)).source("argentinadatos.com")
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("MEP"))
            .willReturn(Optional.of(entity));

        ExchangeRateResponse response = macroDataService.getLatestMepRate();

        assertThat(response.rateType()).isEqualTo("MEP");
        assertThat(response.buy()).isEqualTo("1200.0000");
        assertThat(response.sell()).isEqualTo("1202.0000");
    }

    @Test
    @DisplayName("getLatestMepRate lanza VectisException 404 cuando la DB esta vacia")
    void getLatestMepRate_throwsWhenEmpty() {
        given(exchangeRateRepository.findTopByRateTypeOrderByRateDateDesc("MEP"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> macroDataService.getLatestMepRate())
            .isInstanceOf(VectisException.class)
            .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── getLatestInflation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getLatestInflation retorna InflationResponse con String exacto")
    void getLatestInflation_returnsResponseWhenExists() {
        InflationRecord entity = InflationRecord.builder()
            .id(UUID.randomUUID()).periodDate(LocalDate.of(2026, 5, 31))
            .monthlyRate(new BigDecimal("2.4000")).source("argentinadatos.com")
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();

        given(inflationRecordRepository.findTopByOrderByPeriodDateDesc())
            .willReturn(Optional.of(entity));

        InflationResponse response = macroDataService.getLatestInflation();

        assertThat(response.monthlyRate()).isEqualTo("2.4000");
        assertThat(response.periodDate()).isEqualTo("2026-05-31");
    }

    @Test
    @DisplayName("getLatestInflation lanza VectisException 404 cuando la DB esta vacia")
    void getLatestInflation_throwsWhenEmpty() {
        given(inflationRecordRepository.findTopByOrderByPeriodDateDesc())
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> macroDataService.getLatestInflation())
            .isInstanceOf(VectisException.class)
            .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }
}
