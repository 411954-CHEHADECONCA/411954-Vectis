package com.vectis.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PpiMarketDataClient")
class PpiMarketDataClientTest {

    @Mock private RestTemplate restTemplate;

    private PpiMarketDataClient client;
    /** Spy that lets us stub the package-private getToken() for market-data tests. */
    private PpiMarketDataClient spyClient;

    private static final String BASE_URL          = "https://clientapi.portfoliopersonal.com";
    private static final String AUTHORIZED_CLIENT = "API_CLI_REST";
    private static final String CLIENT_KEY        = "pp19CliApp12";
    private static final String API_KEY           = "UW1JaTdwTkZuRUczSDlZbTFOSm8=";
    private static final String API_SECRET        = "ZjNiMTdhMjAtMjAyMi00MDRmLThmY2EtZDdjNWViMDc5ZDVi";

    @BeforeEach
    void setUp() {
        client = new PpiMarketDataClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl",          BASE_URL);
        ReflectionTestUtils.setField(client, "authorizedClient", AUTHORIZED_CLIENT);
        ReflectionTestUtils.setField(client, "clientKey",        CLIENT_KEY);
        ReflectionTestUtils.setField(client, "apiKey",           API_KEY);
        ReflectionTestUtils.setField(client, "apiSecret",        API_SECRET);

        spyClient = spy(client);
    }

    // ─── isConfigured ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isConfigured retorna false cuando authorizedClient está en blanco")
    void isConfigured_returnsFalse_whenAuthorizedClientIsBlank() {
        ReflectionTestUtils.setField(client, "authorizedClient", "");

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retorna false cuando apiKey está en blanco")
    void isConfigured_returnsFalse_whenApiKeyIsBlank() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retorna false cuando clientKey está en blanco")
    void isConfigured_returnsFalse_whenClientKeyIsBlank() {
        ReflectionTestUtils.setField(client, "clientKey", "");

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retorna false cuando apiSecret está en blanco")
    void isConfigured_returnsFalse_whenApiSecretIsBlank() {
        ReflectionTestUtils.setField(client, "apiSecret", "");

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retorna true cuando todas las credenciales están presentes")
    void isConfigured_returnsTrue_whenAllCredentialsPresent() {
        assertThat(client.isConfigured()).isTrue();
    }

    // ─── getPriceForDate — not configured ────────────────────────────────────

    @Test
    @DisplayName("getPriceForDate retorna empty y no llama RestTemplate cuando no está configurado")
    void getPriceForDate_returnsEmpty_whenNotConfigured() {
        ReflectionTestUtils.setField(client, "authorizedClient", "");

        Optional<PpiMarketDataClient.DatedPrice> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    // ─── Token refresh ────────────────────────────────────────────────────────

    @Test
    @DisplayName("token ausente provoca llamada a LoginApi antes de hacer la consulta de mercado")
    void getPriceForDate_callsLoginApi_whenTokenStateIsEmpty() {
        // TokenState is EMPTY by default → triggers refresh
        // Stub login to return null body — token stays null after refresh
        given(restTemplate.exchange(
                contains("/api/1/Account/LoginApi"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(Class.class)))
                .willAnswer(inv -> ResponseEntity.ok(null));

        Optional<PpiMarketDataClient.DatedPrice> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
        // LoginApi must have been called exactly once
        verify(restTemplate, times(1)).exchange(
                contains("/api/1/Account/LoginApi"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(Class.class));
        // Market data endpoint must NOT have been called (no valid token after failed refresh)
        verify(restTemplate, never()).exchange(
                contains("/api/1/MarketData/Search"),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(Class.class));
    }

    @Test
    @DisplayName("excepción durante login devuelve empty sin propagar (resiliencia)")
    void getPriceForDate_returnsEmpty_whenLoginFails() {
        given(restTemplate.exchange(
                contains("/api/1/Account/LoginApi"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(Class.class)))
                .willThrow(new RuntimeException("connection refused"));

        Optional<PpiMarketDataClient.DatedPrice> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
    }

    // ─── Market data parsing — using spyClient with stubbed getToken() ─────────

    @Test
    @DisplayName("respuesta nula de MarketData/Search retorna Optional.empty()")
    void getPriceForDate_returnsEmpty_whenMarketDataResponseIsNull() {
        // Stub getToken() so no login call is needed
        doReturn("pre-seeded-token").when(spyClient).getToken();

        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willAnswer(inv -> ResponseEntity.ok(null));

        Optional<PpiMarketDataClient.DatedPrice> result = spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("excepción del RestTemplate en búsqueda de mercado retorna Optional.empty() (resiliencia)")
    void getPriceForDate_returnsEmpty_whenMarketDataCallFails() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willThrow(new RuntimeException("timeout"));

        Optional<PpiMarketDataClient.DatedPrice> result = spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("MarketData/Search incluye headers AuthorizedClient y ClientKey además del Bearer")
    void getPriceForDate_sendsAuthorizedClientAndClientKeyHeaders() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        // Body nulo: basta para que el request se ejecute y poder capturar los headers,
        // sin depender del record privado MarketDataItem de la clase de producción.
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willAnswer(inv -> ResponseEntity.ok(null));

        spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 26));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                captor.capture(),
                any(Class.class));

        HttpHeaders sent = captor.getValue().getHeaders();
        assertThat(sent.getFirst("AuthorizedClient")).isEqualTo(AUTHORIZED_CLIENT);
        assertThat(sent.getFirst("ClientKey")).isEqualTo(CLIENT_KEY);
        assertThat(sent.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer pre-seeded-token");
    }

    @Test
    @DisplayName("fallback: ante una ventana de varios días elige el cierre más reciente ≤ fecha")
    void getPriceForDate_picksMostRecentCloseOnOrBeforeRequestedDate() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        // Fecha pedida = sábado 2026-06-27 (sin mercado). La ventana trae el jueves y viernes;
        // debe devolver el viernes 26 (963.0000), el cierre más reciente ≤ fecha.
        PpiMarketDataClient.MarketDataItem[] body = {
                new PpiMarketDataClient.MarketDataItem("2026-06-25T00:00:00-03:00", new BigDecimal("96590")),
                new PpiMarketDataClient.MarketDataItem("2026-06-26T00:00:00-03:00", new BigDecimal("96300")),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willReturn(ResponseEntity.ok(body));

        Optional<PpiMarketDataClient.DatedPrice> result =
                spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 27));

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("963.0000"));
        assertThat(result.get().date()).isEqualTo(LocalDate.of(2026, 6, 26)); // fecha real del cierre
    }

    @Test
    @DisplayName("lookback amplio: consulta 45 días atrás y recupera un cierre lejano (instrumento poco líquido)")
    void getPriceForDate_findsCloseFromWeeksAgo_forIlliquidInstrument() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        // Instrumento ilíquido: último cierre 2026-05-22, se pide precio al 2026-06-25 (34 días después).
        // Con la ventana anterior de 7 días no habría dato; con la ventana ampliada (45) sí lo encuentra.
        PpiMarketDataClient.MarketDataItem[] body = {
                new PpiMarketDataClient.MarketDataItem("2026-05-22T00:00:00-03:00", new BigDecimal("22400")),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willReturn(ResponseEntity.ok(body));

        Optional<PpiMarketDataClient.DatedPrice> result =
                spyClient.getPriceForDate("MRCAO", "ON", LocalDate.of(2026, 6, 25));

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("224.0000"));
        // La valuación se debe fechar en el cierre REAL (22/05), no en la fecha solicitada (25/06).
        assertThat(result.get().date()).isEqualTo(LocalDate.of(2026, 5, 22));

        // Verifica que la ventana consultada es de 45 días (DateFrom = fecha − 45 = 2026-05-11),
        // no la anterior de 7 días — la constante LOOKBACK_DAYS efectivamente se amplió.
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class));
        assertThat(urlCaptor.getValue())
                .contains("DateFrom=2026-05-11")
                .contains("DateTo=2026-06-25");
    }

    @Test
    @DisplayName("fallback: ignora cierres posteriores a la fecha pedida")
    void getPriceForDate_ignoresClosesAfterRequestedDate() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        // Pedimos el 2026-06-25; aunque la respuesta incluya el 26, debe devolver el 25 (965.9000).
        PpiMarketDataClient.MarketDataItem[] body = {
                new PpiMarketDataClient.MarketDataItem("2026-06-25T00:00:00-03:00", new BigDecimal("96590")),
                new PpiMarketDataClient.MarketDataItem("2026-06-26T00:00:00-03:00", new BigDecimal("96300")),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willReturn(ResponseEntity.ok(body));

        Optional<PpiMarketDataClient.DatedPrice> result =
                spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("965.9000"));
        assertThat(result.get().date()).isEqualTo(LocalDate.of(2026, 6, 25));
    }

    // ─── getPriceSeries ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPriceSeries retorna lista vacía y no llama RestTemplate cuando no está configurado")
    void getPriceSeries_returnsEmpty_whenNotConfigured() {
        ReflectionTestUtils.setField(client, "apiSecret", "");

        var result = client.getPriceSeries("AL30", "BONOS",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getPriceSeries retorna lista vacía cuando el rango es inválido (from > to)")
    void getPriceSeries_returnsEmpty_whenRangeInverted() {
        var result = client.getPriceSeries("AL30", "BONOS",
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 2, 1));

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getPriceSeries devuelve toda la serie normalizada, ordenada y sin fechas fuera de rango")
    void getPriceSeries_returnsNormalizedSortedSeries() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.MarketDataItem[] body = {
                new PpiMarketDataClient.MarketDataItem("2026-03-02T00:00:00-03:00", new BigDecimal("96300")),
                new PpiMarketDataClient.MarketDataItem("2026-03-01T00:00:00-03:00", new BigDecimal("96590")),
                // fuera de rango (posterior al 'to'): debe descartarse
                new PpiMarketDataClient.MarketDataItem("2026-06-30T00:00:00-03:00", new BigDecimal("99000")),
                // precio nulo: debe descartarse
                new PpiMarketDataClient.MarketDataItem("2026-03-03T00:00:00-03:00", null),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willReturn(ResponseEntity.ok(body));

        var result = spyClient.getPriceSeries("AL30", "BONOS",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(2);
        // ordenado ascendente por fecha
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.get(0).price()).isEqualByComparingTo("965.9000");
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(result.get(1).price()).isEqualByComparingTo("963.0000");
    }

    @Test
    @DisplayName("getPriceSeries retorna lista vacía ante excepción del RestTemplate (resiliencia)")
    void getPriceSeries_returnsEmpty_whenCallFails() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        given(restTemplate.exchange(
                contains("/api/1/MarketData/Search"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(Class.class)))
                .willThrow(new RuntimeException("timeout"));

        var result = spyClient.getPriceSeries("AL30", "BONOS",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).isEmpty();
    }

    // ─── searchInstruments / instrumentExists ─────────────────────────────────

    @Test
    @DisplayName("searchInstruments retorna lista vacía y no llama RestTemplate cuando no está configurado")
    void searchInstruments_returnsEmpty_whenNotConfigured() {
        ReflectionTestUtils.setField(client, "apiSecret", "");

        var result = client.searchInstruments("LETRA TESORO", "");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("searchInstruments parsea la respuesta y descarta items sin ticker")
    void searchInstruments_parsesResponse() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiInstrument[] body = {
                new PpiMarketDataClient.PpiInstrument("S30N6", "LETRA TESORO NACIONAL CAPITALIZABLE 30/11/26 $", "LETRAS", "Pesos"),
                new PpiMarketDataClient.PpiInstrument("S31G6", "LETRA TESORO NACIONAL CAPITALIZABLE 31/08/26 $", "LETRAS", "Pesos"),
                new PpiMarketDataClient.PpiInstrument(null, "sin ticker — se descarta", "LETRAS", "Pesos"),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/SearchInstrument"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiInstrument[].class)))
                .willReturn(ResponseEntity.ok(body));

        var result = spyClient.searchInstruments("LETRA TESORO", "");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PpiMarketDataClient.PpiInstrument::ticker)
                .containsExactly("S30N6", "S31G6");
    }

    @Test
    @DisplayName("searchInstruments retorna lista vacía ante excepción del RestTemplate (resiliencia)")
    void searchInstruments_returnsEmpty_whenCallFails() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        given(restTemplate.exchange(
                contains("/api/1/MarketData/SearchInstrument"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiInstrument[].class)))
                .willThrow(new RuntimeException("timeout"));

        var result = spyClient.searchInstruments("LETRA TESORO", "");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("instrumentExists true cuando PPI devuelve el ticker exacto; false si no aparece")
    void instrumentExists_matchesExactTicker() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiInstrument[] body = {
                new PpiMarketDataClient.PpiInstrument("S31L6", "LETRA ... 31/07/26 $", "LETRAS", "Pesos"),
        };
        given(restTemplate.exchange(
                contains("/api/1/MarketData/SearchInstrument"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiInstrument[].class)))
                .willReturn(ResponseEntity.ok(body));

        assertThat(spyClient.instrumentExists("S31L6")).isTrue();
        assertThat(spyClient.instrumentExists("S18D6")).isFalse(); // no está en la respuesta
    }

    @Test
    @DisplayName("instrumentExists false y no llama a PPI cuando el ticker tiene menos de 2 chars")
    void instrumentExists_false_whenTickerTooShort() {
        assertThat(spyClient.instrumentExists("S")).isFalse();
        assertThat(spyClient.instrumentExists(null)).isFalse();
    }

    // ─── BigDecimal normalization (pure unit tests) ───────────────────────────

    @Test
    @DisplayName("normalización: 96300 / 100 = 963.0000 con RoundingMode.HALF_EVEN")
    void normalizePrice_96300_dividedBy100_gives963() {
        BigDecimal rawPrice   = new BigDecimal("96300");
        BigDecimal normalized = rawPrice.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);

        assertThat(normalized).isEqualByComparingTo("963.0000");
    }

    @Test
    @DisplayName("normalización: 98550 / 100 = 985.5000")
    void normalizePrice_98550_dividedBy100_gives985_5() {
        BigDecimal rawPrice   = new BigDecimal("98550");
        BigDecimal normalized = rawPrice.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);

        assertThat(normalized).isEqualByComparingTo("985.5000");
    }

    @Test
    @DisplayName("normalización con precio fraccionario: 96325.5 / 100 = 963.2550")
    void normalizePrice_fractional_halfEvenRounding() {
        BigDecimal rawPrice   = new BigDecimal("96325.5");
        BigDecimal normalized = rawPrice.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);

        assertThat(normalized).isEqualByComparingTo("963.2550");
    }

    // ─── getBondEstimate ───────────────────────────────────────────────────────

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Carga y parsea un fixture real de PPI Bonds/Estimate capturado en la Fase 0. */
    private static PpiMarketDataClient.PpiBondEstimateResponseDto loadFixture(String name) throws Exception {
        try (var in = PpiMarketDataClientTest.class.getResourceAsStream("/ppi/" + name)) {
            return JSON.readValue(in, PpiMarketDataClient.PpiBondEstimateResponseDto.class);
        }
    }

    @Test
    @DisplayName("getBondEstimate retorna empty y no llama RestTemplate cuando no está configurado")
    void getBondEstimate_returnsEmpty_whenNotConfigured() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                client.getBondEstimate("AL30", LocalDate.of(2026, 7, 7));

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getBondEstimate retorna empty ante excepción del RestTemplate (resiliencia)")
    void getBondEstimate_returnsEmpty_whenCallFails() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willThrow(new RuntimeException("timeout"));

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                spyClient.getBondEstimate("AL30", LocalDate.of(2026, 7, 7));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getBondEstimate retorna empty cuando la respuesta no tiene flows")
    void getBondEstimate_returnsEmpty_whenResponseHasNoFlows() {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiBondEstimateResponseDto emptyBody =
                new PpiMarketDataClient.PpiBondEstimateResponseDto(java.util.List.of(), "US$", "Dólar", null);
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willReturn(ResponseEntity.ok(emptyBody));

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                spyClient.getBondEstimate("AL30", LocalDate.of(2026, 7, 7));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getBondEstimate parsea el fixture real de AL30: flows, residualValue y moneda US$ → USD")
    void getBondEstimate_parsesRealAl30Fixture() throws Exception {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiBondEstimateResponseDto fixture = loadFixture("estimate_al30_100.json");
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willReturn(ResponseEntity.ok(fixture));

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                spyClient.getBondEstimate("AL30", LocalDate.of(2026, 7, 7));

        assertThat(result).isPresent();
        PpiMarketDataClient.PpiBondEstimate estimate = result.get();
        assertThat(estimate.flows()).hasSize(9);
        assertThat(estimate.abbreviationCurrencyPay()).isEqualTo("US$");

        PpiMarketDataClient.PpiBondFlow first = estimate.flows().get(0);
        assertThat(first.cuttingDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(first.residualValue()).isEqualByComparingTo("0.72");
        assertThat(first.rent()).isEqualByComparingTo("0.27");
        assertThat(first.amortization()).isEqualByComparingTo("8");

        assertThat(PpiMarketDataClient.normalizePpiCurrency(estimate.abbreviationCurrencyPay()))
                .contains("USD");
    }

    @Test
    @DisplayName("getBondEstimate parsea el fixture real de TX26 (bono CER): moneda AR$ → ARS")
    void getBondEstimate_parsesRealTx26Fixture_arsCurrency() throws Exception {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiBondEstimateResponseDto fixture = loadFixture("estimate_tx26_100.json");
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willReturn(ResponseEntity.ok(fixture));

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                spyClient.getBondEstimate("TX26", LocalDate.of(2026, 7, 7));

        assertThat(result).isPresent();
        assertThat(result.get().abbreviationCurrencyPay()).isEqualTo("AR$");
        assertThat(PpiMarketDataClient.normalizePpiCurrency(result.get().abbreviationCurrencyPay()))
                .contains("ARS");
    }

    @Test
    @DisplayName("normalizePpiCurrency: literal desconocido/vacío/null devuelve empty (el caller aplica su fallback)")
    void normalizePpiCurrency_unknownLiteral_returnsEmpty() {
        assertThat(PpiMarketDataClient.normalizePpiCurrency("Yenes")).isEmpty();
        assertThat(PpiMarketDataClient.normalizePpiCurrency("XYZ")).isEmpty();
        assertThat(PpiMarketDataClient.normalizePpiCurrency("")).isEmpty();
        assertThat(PpiMarketDataClient.normalizePpiCurrency("   ")).isEmpty();
        assertThat(PpiMarketDataClient.normalizePpiCurrency(null)).isEmpty();
    }

    @Test
    @DisplayName("normalizePpiCurrency: reconoce símbolos y palabras (con acentos) para USD y ARS")
    void normalizePpiCurrency_recognizedLiterals() {
        // USD: símbolos y palabras (insensible a acentos/mayúsculas)
        assertThat(PpiMarketDataClient.normalizePpiCurrency("US$")).contains("USD");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("U$S")).contains("USD");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("Dólar")).contains("USD");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("Dólares")).contains("USD");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("USD")).contains("USD");
        // ARS: símbolo y palabras
        assertThat(PpiMarketDataClient.normalizePpiCurrency("AR$")).contains("ARS");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("Pesos")).contains("ARS");
        assertThat(PpiMarketDataClient.normalizePpiCurrency("ARS")).contains("ARS");
        // Peso-first: "dólar linked" que liquida en pesos → ARS
        assertThat(PpiMarketDataClient.normalizePpiCurrency("Pesos (dólar linked)")).contains("ARS");
    }

    @Test
    @DisplayName("getBondEstimate envía los parámetros fijos obligatorios (QuantityType, Quantity, Price, etc.)")
    void getBondEstimate_sendsFixedRequiredQueryParams() throws Exception {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiBondEstimateResponseDto fixture = loadFixture("estimate_al30_100.json");
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willReturn(ResponseEntity.ok(fixture));

        spyClient.getBondEstimate("AL30", LocalDate.of(2026, 1, 15));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class));

        String url = urlCaptor.getValue();
        assertThat(url)
                .contains("Ticker=AL30")
                .contains("Date=2026-01-15")
                .contains("QuantityType=PAPELES")
                .contains("Quantity=100")
                .contains("Price=100")
                .contains("AmountOfMoney=0")
                .contains("ExchangeRate=0")
                .contains("EquityRate=0")
                .contains("ExchangeRateAmortization=0")
                .contains("RateAdjustmentAmortization=0");
    }

    @Test
    @DisplayName("getBondEstimate: residualAfterPer100 = residualValue×100 − amortization detecta el pago final")
    void getBondEstimate_lastFlow_residualCalculationMatchesFinalPayment() throws Exception {
        doReturn("pre-seeded-token").when(spyClient).getToken();

        PpiMarketDataClient.PpiBondEstimateResponseDto fixture = loadFixture("estimate_al30_100.json");
        given(restTemplate.exchange(
                contains("/api/1/MarketData/Bonds/Estimate"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PpiMarketDataClient.PpiBondEstimateResponseDto.class)))
                .willReturn(ResponseEntity.ok(fixture));

        Optional<PpiMarketDataClient.PpiBondEstimate> result =
                spyClient.getBondEstimate("AL30", LocalDate.of(2026, 1, 15));

        // Último flow real del fixture AL30: residualValue=0.08, amortization=8 → residual final = 0
        PpiMarketDataClient.PpiBondFlow last = result.get().flows().get(result.get().flows().size() - 1);
        BigDecimal residualAfter = last.residualValue().multiply(BigDecimal.valueOf(100)).subtract(last.amortization());
        assertThat(residualAfter).isEqualByComparingTo("0");
    }
}
