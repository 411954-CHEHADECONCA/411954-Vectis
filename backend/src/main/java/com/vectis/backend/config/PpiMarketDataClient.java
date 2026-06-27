package com.vectis.backend.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client for Portfolio Personal Inversiones (PPI) Market Data API.
 *
 * Auth: POST /api/1/Account/LoginApi with 4 headers (AuthorizedClient, ClientKey, ApiKey, ApiSecret).
 * Returns {@code accessToken} + {@code expirationDate} (ISO offset datetime).
 *
 * Token state is stored as an immutable {@link TokenState} record in an {@link AtomicReference},
 * eliminating TOCTOU races between the guard check and the synchronized refresh.
 *
 * All public methods degrade gracefully: if credentials are not fully configured or any call
 * fails, they return empty results without throwing an exception.
 */
@Slf4j
@Component
public class PpiMarketDataClient {

    private final RestTemplate restTemplate;

    @Value("${ppi.api.base-url:https://clientapi.portfoliopersonal.com}")
    private String baseUrl;

    @Value("${ppi.authorized-client:}")
    private String authorizedClient;

    @Value("${ppi.client-key:}")
    private String clientKey;

    @Value("${ppi.api-key:}")
    private String apiKey;

    @Value("${ppi.api-secret:}")
    private String apiSecret;

    /** Normalization divisor: all PPI instruments have nominalInPrice = 100. */
    private static final BigDecimal NOMINAL_IN_PRICE = BigDecimal.valueOf(100);

    /**
     * Immutable token state — stored atomically to avoid TOCTOU between guard and refresh.
     * expiresAtMillis is the epoch-millis timestamp when the token expires on PPI's side.
     */
    private record TokenState(String token, long expiresAtMillis) {
        static final TokenState EMPTY = new TokenState(null, 0L);

        boolean isValid() {
            return token != null && System.currentTimeMillis() <= expiresAtMillis - 60_000L;
        }
    }

    private final AtomicReference<TokenState> tokenState = new AtomicReference<>(TokenState.EMPTY);

    public PpiMarketDataClient(@Qualifier("macroRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns whether all four PPI credentials are non-blank.
     * Services should call this before invoking {@link #getPriceForDate}.
     */
    public boolean isConfigured() {
        return StringUtils.hasText(authorizedClient)
                && StringUtils.hasText(clientKey)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret);
    }

    /**
     * Fetches the price for the given ticker and instrument type on the specified date.
     * Calls GET /api/1/MarketData/Search with A-48HS settlement and normalizes the raw price
     * by dividing by 100 (nominalInPrice convention for all PPI instruments).
     *
     * @param ticker  e.g. "AL30", "S31G5"
     * @param ppiType PPI instrument type string, e.g. "BONOS", "LETRAS", "ON"
     * @param fecha   the date to query
     * @return normalized price (rawPrice / 100), or empty if not configured, no data, or failure
     */
    public Optional<BigDecimal> getPriceForDate(String ticker, String ppiType, LocalDate fecha) {
        if (!isConfigured()) {
            log.warn("PPI client no configurado — se omite getPriceForDate para {}", ticker);
            return Optional.empty();
        }
        try {
            String token = getToken();
            if (token == null) return Optional.empty();

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/MarketData/Search")
                    .queryParam("Ticker",     ticker)
                    .queryParam("Type",       ppiType)
                    .queryParam("DateFrom",   fecha.toString())
                    .queryParam("DateTo",     fecha.toString())
                    .queryParam("Settlement", "A-48HS")
                    .build()
                    .toUriString();

            ResponseEntity<MarketDataItem[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), MarketDataItem[].class);

            MarketDataItem[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.debug("PPI MarketData/Search vacío para ticker={} type={} fecha={}", ticker, ppiType, fecha);
                return Optional.empty();
            }

            BigDecimal rawPrice = body[0].price();
            if (rawPrice == null) {
                log.debug("PPI MarketData/Search devolvió price=null para ticker={}", ticker);
                return Optional.empty();
            }

            BigDecimal normalized = rawPrice.divide(NOMINAL_IN_PRICE, 4, RoundingMode.HALF_EVEN);
            log.debug("PPI precio para {}: {} / 100 = {}", ticker, rawPrice, normalized);
            return Optional.of(normalized);

        } catch (Exception e) {
            log.warn("PPI getPriceForDate({}, {}, {}) falló: {}", ticker, ppiType, fecha, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Token management ─────────────────────────────────────────────────────

    /**
     * Returns a valid bearer token. Refreshes atomically when the current state is invalid.
     * Package-private for unit testing.
     */
    String getToken() {
        TokenState current = tokenState.get();
        if (!current.isValid()) {
            refreshToken();
        }
        return tokenState.get().token();
    }

    private synchronized void refreshToken() {
        // Double-checked under the monitor: another thread may have refreshed while we waited
        if (tokenState.get().isValid()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("AuthorizedClient", authorizedClient);
            headers.set("ClientKey",        clientKey);
            headers.set("ApiKey",           apiKey);
            headers.set("ApiSecret",        apiSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String loginUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/Account/LoginApi")
                    .build()
                    .toUriString();

            ResponseEntity<PpiTokenDto> response = restTemplate.exchange(
                    loginUrl, HttpMethod.POST,
                    new HttpEntity<>("{}", headers),
                    PpiTokenDto.class);

            PpiTokenDto dto = response.getBody();
            if (dto == null || dto.accessToken() == null) {
                log.warn("PPI token response fue nula o sin accessToken");
                tokenState.set(TokenState.EMPTY);
                return;
            }

            // expirationDate comes as ISO offset datetime: "2026-06-27T01:56:12-03:00"
            long expiresAtMillis;
            if (dto.expirationDate() != null) {
                expiresAtMillis = OffsetDateTime.parse(dto.expirationDate()).toInstant().toEpochMilli();
            } else {
                // Fallback: 1 hour if no expiration provided
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L;
            }

            tokenState.set(new TokenState(dto.accessToken(), expiresAtMillis));
            log.debug("PPI token refrescado, expira en: {}", dto.expirationDate());

        } catch (Exception e) {
            log.warn("PPI token refresh falló: {}", e.getMessage());
            tokenState.set(TokenState.EMPTY);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    // ─── Internal API response DTOs ───────────────────────────────────────────

    private record PpiTokenDto(
            @JsonProperty("accessToken")    String accessToken,
            @JsonProperty("expirationDate") String expirationDate
    ) {}

    private record MarketDataItem(
            @JsonProperty("price") BigDecimal price
    ) {}
}
