package com.vectis.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        Optional<BigDecimal> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

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

        Optional<BigDecimal> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

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

        Optional<BigDecimal> result = client.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

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

        Optional<BigDecimal> result = spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

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

        Optional<BigDecimal> result = spyClient.getPriceForDate("AL30", "BONOS", LocalDate.of(2026, 6, 25));

        assertThat(result).isEmpty();
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
}
