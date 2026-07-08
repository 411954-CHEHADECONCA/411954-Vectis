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
import java.util.Arrays;
import java.util.Comparator;
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
     * Días hacia atrás a consultar desde la fecha pedida para encontrar el último cierre
     * disponible. Cubre fines de semana largos, feriados y además instrumentos poco líquidos
     * (p. ej. ciertos ON que pueden pasar semanas sin cotizar): así el "precio de hoy" cae al
     * último cierre disponible en lugar de devolver vacío.
     */
    private static final int LOOKBACK_DAYS = 45;

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
     *
     * Queries a lookback window of {@link #LOOKBACK_DAYS} days ending on {@code fecha} via
     * GET /api/1/MarketData/Search (A-48HS settlement) and returns the price of the most recent
     * available close on or before {@code fecha}. This way, requesting a price for a weekend,
     * holiday, or the current (still-open) day falls back to the latest available close
     * instead of returning empty — i.e. "el precio de hoy o lo más nuevo que venga de la API".
     *
     * The raw price is normalized by dividing by 100 (nominalInPrice convention for all PPI
     * instruments).
     *
     * <p>Devuelve un {@link DatedPrice} con la <b>fecha real del cierre</b> elegido (no la fecha
     * solicitada): para instrumentos ilíquidos ese cierre puede ser de varias semanas atrás, y quien
     * persista la valuación debe usar esa fecha verídica en vez de "hoy" para no falsear el histórico.
     *
     * @param ticker  e.g. "AL30", "S31G5"
     * @param ppiType PPI instrument type string, e.g. "BONOS", "LETRAS", "ON"
     * @param fecha   the date to query (upper bound of the lookback window)
     * @return the latest close ≤ fecha as a {@link DatedPrice} (real close date + normalized price),
     *         or empty if not configured, no data in the window, or failure
     */
    public Optional<DatedPrice> getPriceForDate(String ticker, String ppiType, LocalDate fecha) {
        if (!isConfigured()) {
            log.warn("PPI client no configurado — se omite getPriceForDate para {}", ticker);
            return Optional.empty();
        }
        try {
            String token = getToken();
            if (token == null) return Optional.empty();

            LocalDate from = fecha.minusDays(LOOKBACK_DAYS);
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/MarketData/Search")
                    .queryParam("Ticker",     ticker)
                    .queryParam("Type",       ppiType)
                    .queryParam("DateFrom",   from.toString())
                    .queryParam("DateTo",     fecha.toString())
                    .queryParam("Settlement", "A-48HS")
                    .build()
                    .toUriString();

            ResponseEntity<MarketDataItem[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), MarketDataItem[].class);

            MarketDataItem[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.debug("PPI MarketData/Search vacío para ticker={} type={} ventana={}..{}",
                        ticker, ppiType, from, fecha);
                return Optional.empty();
            }

            // Elegir el cierre más reciente con fecha ≤ fecha solicitada y precio no nulo.
            MarketDataItem latest = Arrays.stream(body)
                    .filter(i -> i.price() != null && i.date() != null)
                    .filter(i -> !i.dateAsLocalDate().isAfter(fecha))
                    .max(Comparator.comparing(MarketDataItem::dateAsLocalDate))
                    .orElse(null);

            if (latest == null) {
                log.debug("PPI MarketData/Search sin precios válidos ≤ {} para ticker={}", fecha, ticker);
                return Optional.empty();
            }

            BigDecimal normalized = latest.price().divide(NOMINAL_IN_PRICE, 4, RoundingMode.HALF_EVEN);
            log.debug("PPI precio para {} (solicitado {}, cierre {}): {} / 100 = {}",
                    ticker, fecha, latest.dateAsLocalDate(), latest.price(), normalized);
            return Optional.of(new DatedPrice(latest.dateAsLocalDate(), normalized));

        } catch (Exception e) {
            log.warn("PPI getPriceForDate({}, {}, {}) falló: {}", ticker, ppiType, fecha, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches the full daily price series for the given ticker between {@code from} and {@code to}
     * (inclusive) via GET /api/1/MarketData/Search (A-48HS settlement). Unlike {@link #getPriceForDate},
     * it returns every available close in the range (not just the latest), normalized by dividing by 100.
     *
     * Used to backfill the historical valuation series of a LETRA/BONO/ON when it is created with a
     * past purchase date, mirroring the FCI cuotapartes backfill.
     *
     * @return a list of {@link DatedPrice} (date + normalized price) sorted ascending by date,
     *         or an empty list if not configured, no data, or failure (degrades gracefully).
     */
    public List<DatedPrice> getPriceSeries(String ticker, String ppiType, LocalDate from, LocalDate to) {
        if (!isConfigured()) {
            log.warn("PPI client no configurado — se omite getPriceSeries para {}", ticker);
            return List.of();
        }
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        try {
            String token = getToken();
            if (token == null) return List.of();

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/MarketData/Search")
                    .queryParam("Ticker",     ticker)
                    .queryParam("Type",       ppiType)
                    .queryParam("DateFrom",   from.toString())
                    .queryParam("DateTo",     to.toString())
                    .queryParam("Settlement", "A-48HS")
                    .build()
                    .toUriString();

            ResponseEntity<MarketDataItem[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), MarketDataItem[].class);

            MarketDataItem[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.debug("PPI MarketData/Search (serie) vacío para ticker={} type={} rango={}..{}",
                        ticker, ppiType, from, to);
                return List.of();
            }

            return Arrays.stream(body)
                    .filter(i -> i.price() != null && i.date() != null)
                    .filter(i -> {
                        LocalDate d = i.dateAsLocalDate();
                        return !d.isBefore(from) && !d.isAfter(to);
                    })
                    .map(i -> new DatedPrice(i.dateAsLocalDate(),
                            i.price().divide(NOMINAL_IN_PRICE, 4, RoundingMode.HALF_EVEN)))
                    .sorted(Comparator.comparing(DatedPrice::date))
                    .toList();

        } catch (Exception e) {
            log.warn("PPI getPriceSeries({}, {}, {}..{}) falló: {}", ticker, ppiType, from, to, e.getMessage());
            return List.of();
        }
    }

    /** A single close: its date and its normalized price (rawPrice / 100). */
    public record DatedPrice(LocalDate date, BigDecimal price) {}

    // ─── Bond payment schedule (Bonds/Estimate) ───────────────────────────────────

    /**
     * Fetches the full payment flow (renta + amortización schedule) of a bond/ON via
     * {@code GET /api/1/MarketData/Bonds/Estimate}, used to build the payment calendar of a
     * BONO/ON with auto-tracking.
     *
     * <h3>Hallazgos empíricos (Fase 0 — validado contra credenciales reales de PPI)</h3>
     * <ul>
     *   <li><b>Parámetros obligatorios</b>: el endpoint devuelve 400 si falta cualquiera de
     *       {@code Ticker, Date, QuantityType, Quantity, Price, AmountOfMoney, ExchangeRate,
     *       EquityRate, ExchangeRateAmortization, RateAdjustmentAmortization}. Este cliente siempre
     *       llama con {@code QuantityType=PAPELES&Quantity=100&Price=100&AmountOfMoney=0&
     *       ExchangeRate=0&EquityRate=0&ExchangeRateAmortization=0&RateAdjustmentAmortization=0}.</li>
     *   <li><b>Escala de los flows</b>: con {@code Quantity=100} los {@code flows[]} vienen en
     *       montos <b>absolutos por 100 nominales</b> (probado: {@code Quantity=200} escala 2× los
     *       montos; {@code Quantity=100} es la base "por 100 VN" que se persiste). {@code Price} NO
     *       afecta los flows (sólo la TIR/sensitivity) — se manda fijo en 100 por convención.</li>
     *   <li><b>Forma de la respuesta</b>: a diferencia de {@code MarketData/Search}, este endpoint
     *       devuelve un <b>único objeto JSON</b> (no un array) — confirmado contra fixtures reales
     *       capturados en {@code src/test/resources/ppi/}. Se deserializa directo a
     *       {@link PpiBondEstimateResponseDto}, sin envolver en {@code List}/array.</li>
     *   <li><b>Moneda de pago</b>: viene en los campos top-level {@code currency}/
     *       {@code abbreviationCurrencyPay} — típicamente los símbolos {@code "US$"} (dólar) y
     *       {@code "AR$"} (peso) — mientras que los {@code flows[].currency} individuales vienen
     *       siempre {@code null}. Se normalizan vía {@link #normalizePpiCurrency}, que tolera tanto
     *       símbolos como palabras ({@code "Dólares"}, {@code "Pesos"}, {@code "USD"}, …); un literal
     *       no reconocido cae a {@link Optional#empty()} para que el caller decida el fallback
     *       (típicamente {@code issueCurrency} y luego la currency del propio activo).</li>
     *   <li><b>Semántica de {@code Date}</b>: define desde cuándo se devuelven flujos. Con la fecha
     *       de hoy sólo trae cortes futuros; con una fecha pasada (p. ej. la {@code purchaseDate}
     *       del activo) incluye también los cortes ya vencidos desde esa fecha — necesario para
     *       capturar pagos que ya vencieron pero el usuario nunca confirmó/cobró.</li>
     *   <li><b>Formato de fecha de los flows</b>: {@code flows[].cuttingDate} viene como ISO offset
     *       datetime (p. ej. {@code "2026-07-09T00:00:00-03:00"}), se trunca a {@link LocalDate}.</li>
     *   <li><b>{@code residualValue} es una FRACCIÓN (0..1) ANTES del pago del flow correspondiente</b>
     *       (no un monto por 100). Ejemplo real (AL30): primer flow {@code residualValue=0.72,
     *       amortization=8} → el residual DESPUÉS de ese pago, por 100 nominales, es
     *       {@code 0.72 × 100 − 8 = 64}, que coincide con el {@code residualValue=0.64} del
     *       siguiente flow. Quien persista el calendario debe calcular
     *       {@code residualAfterPer100 = residualValue × 100 − amortization} para detectar el pago
     *       final (residual 0).</li>
     * </ul>
     *
     * @param ticker           e.g. "AL30", "TX26"
     * @param date             lower bound for the flows to return — pass the asset's purchase date
     *                         to capture overdue uncollected payments, not just future ones
     * @return the parsed estimate (flows + payment currency + issue metadata), or empty if not
     *         configured, the HTTP call fails, or the response has no flows (degrades gracefully,
     *         never throws)
     */
    public Optional<PpiBondEstimate> getBondEstimate(String ticker, LocalDate date) {
        if (!isConfigured()) {
            log.warn("PPI client no configurado — se omite getBondEstimate para {}", ticker);
            return Optional.empty();
        }
        try {
            String token = getToken();
            if (token == null) return Optional.empty();

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/MarketData/Bonds/Estimate")
                    .queryParam("Ticker",     ticker)
                    .queryParam("Date",       date.toString())
                    .queryParam("QuantityType", "PAPELES")
                    .queryParam("Quantity",   "100")
                    .queryParam("Price",      "100")
                    .queryParam("AmountOfMoney", "0")
                    .queryParam("ExchangeRate",  "0")
                    .queryParam("EquityRate",    "0")
                    .queryParam("ExchangeRateAmortization", "0")
                    .queryParam("RateAdjustmentAmortization", "0")
                    .build()
                    .toUriString();

            ResponseEntity<PpiBondEstimateResponseDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)),
                    PpiBondEstimateResponseDto.class);

            PpiBondEstimateResponseDto body = response.getBody();
            if (body == null || body.flows() == null || body.flows().isEmpty()) {
                log.debug("PPI Bonds/Estimate vacío para ticker={} date={}", ticker, date);
                return Optional.empty();
            }

            List<PpiBondFlow> flows = body.flows().stream()
                    .filter(f -> f.cuttingDate() != null)
                    .map(f -> new PpiBondFlow(
                            parseFlowDate(f.cuttingDate()),
                            nz(f.residualValue()),
                            nz(f.rent()),
                            nz(f.amortization()),
                            f.currency()))
                    .toList();

            return Optional.of(new PpiBondEstimate(
                    flows, body.abbreviationCurrencyPay(), body.issueCurrency(), body.expirationDate()));

        } catch (Exception e) {
            log.warn("PPI getBondEstimate({}, {}) falló: {}", ticker, date, e.getMessage());
            return Optional.empty();
        }
    }

    /** Trunca el ISO offset datetime de un flow ({@code "2026-07-09T00:00:00-03:00"}) a su LocalDate. */
    private static LocalDate parseFlowDate(String iso) {
        return OffsetDateTime.parse(iso).toLocalDate();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Normaliza cualquier literal de moneda cruda de PPI a {@code "USD"}/{@code "ARS"} — fuente
     * única para todos los endpoints (Bonds/Estimate {@code abbreviationCurrencyPay}/{@code issueCurrency}
     * y SearchInstrument {@code currency}). Tolera símbolos ({@code "US$"}, {@code "U$S"}, {@code "AR$"})
     * y palabras ({@code "Dólar"}, {@code "Dólares"}, {@code "USD"}, {@code "Pesos"}, {@code "ARS"}),
     * insensible a acentos y mayúsculas/minúsculas. Devuelve {@link Optional#empty()} ante un literal
     * no reconocido — el caller decide el fallback (típicamente la currency del activo) — y se loguea
     * un warning acá mismo para que el desconocido quede visible sin propagar una excepción.
     */
    public static Optional<String> normalizePpiCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) return Optional.empty();
        // Quita acentos y pasa a mayúsculas para comparar de forma robusta ("Dólares" → "DOLARES").
        String norm = java.text.Normalizer.normalize(rawCurrency.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        // Peso primero: cubre "dólar linked EN PESOS" (liquida en ARS aunque nombre al dólar),
        // consistente con el orden histórico de InstrumentCatalogSyncService.normalizeCurrency.
        if (norm.contains("AR$") || norm.contains("PESO") || norm.contains("ARS")) return Optional.of("ARS");
        if (norm.contains("US$") || norm.contains("U$S") || norm.contains("DOLAR") || norm.contains("USD")) return Optional.of("USD");
        log.warn("Literal de moneda PPI no reconocido: '{}' — el caller debe aplicar su fallback", rawCurrency);
        return Optional.empty();
    }

    /** Flujo de fondos (por 100 nominales) del activo en una fecha de corte — ver {@link #getBondEstimate}. */
    public record PpiBondFlow(
            LocalDate cuttingDate,
            BigDecimal residualValue,
            BigDecimal rent,
            BigDecimal amortization,
            String currency
    ) {}

    /** Estimación completa del flujo de pagos de un bono/ON — ver {@link #getBondEstimate}. */
    public record PpiBondEstimate(
            List<PpiBondFlow> flows,
            String abbreviationCurrencyPay,
            String issueCurrency,
            LocalDate expirationDate
    ) {}

    /**
     * Raw deserialization target for {@code Bonds/Estimate} — a single JSON object, not an array.
     * Package-private (not private) so unit tests in the same package can build fixtures directly.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record PpiBondEstimateResponseDto(
            @JsonProperty("flows")                   List<PpiBondFlowDto> flows,
            @JsonProperty("abbreviationCurrencyPay")  String abbreviationCurrencyPay,
            @JsonProperty("issueCurrency")            String issueCurrency,
            @JsonProperty("expirationDate")           @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = FlexibleDateDeserializer.class) LocalDate expirationDate
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record PpiBondFlowDto(
            @JsonProperty("cuttingDate")    String cuttingDate,
            @JsonProperty("residualValue")  BigDecimal residualValue,
            @JsonProperty("rent")           BigDecimal rent,
            @JsonProperty("amortization")   BigDecimal amortization,
            @JsonProperty("currency")       String currency
    ) {}

    /** Parses PPI's "dd/MM/yyyy" expirationDate string to LocalDate; null-safe. */
    private static class FlexibleDateDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDate> {
        private static final java.time.format.DateTimeFormatter FMT =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        @Override
        public LocalDate deserialize(com.fasterxml.jackson.core.JsonParser p,
                                      com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) return null;
            try {
                return LocalDate.parse(text, FMT);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ─── Instrument search / validation ─────────────────────────────────────────

    /**
     * Searches PPI's instrument universe via GET /api/1/MarketData/SearchInstrument. PPI treats this
     * as an autocomplete: at least one of {@code name}/{@code tickerPrefix} must have ≥2 chars, and it
     * returns fuzzy, mixed-type matches. Used to keep the local catalog fresh: discover new
     * instruments (by name) and validate whether a ticker is still listed (by exact ticker).
     *
     * @return matching instruments (never null); empty if not configured, bad criteria, or failure.
     */
    public List<PpiInstrument> searchInstruments(String name, String tickerPrefix) {
        if (!isConfigured()) {
            log.warn("PPI client no configurado — se omite searchInstruments");
            return List.of();
        }
        try {
            String token = getToken();
            if (token == null) return List.of();

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/1/MarketData/SearchInstrument")
                    .queryParam("Name",   name != null ? name : "")
                    .queryParam("Ticker", tickerPrefix != null ? tickerPrefix : "")
                    .build()
                    .toUriString();

            ResponseEntity<PpiInstrument[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), PpiInstrument[].class);

            PpiInstrument[] body = response.getBody();
            if (body == null || body.length == 0) return List.of();

            return Arrays.stream(body)
                    .filter(i -> i.ticker() != null && !i.ticker().isBlank())
                    .toList();

        } catch (Exception e) {
            log.warn("PPI searchInstruments(name={}, ticker={}) falló: {}", name, tickerPrefix, e.getMessage());
            return List.of();
        }
    }

    /**
     * Whether an exact ticker is currently listed in PPI. Delisted/expired tickers return false, so
     * the catalog can mark them inactive. Uses the ticker as the search criterion and matches exactly.
     */
    public boolean instrumentExists(String ticker) {
        if (ticker == null || ticker.length() < 2) return false;
        return searchInstruments("", ticker).stream()
                .anyMatch(i -> ticker.equalsIgnoreCase(i.ticker()));
    }

    /** One instrument as returned by SearchInstrument. Extra JSON fields are ignored. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record PpiInstrument(
            @JsonProperty("ticker")      String ticker,
            @JsonProperty("description") String description,
            @JsonProperty("type")        String type,
            @JsonProperty("currency")    String currency
    ) {}

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
        // PPI exige AuthorizedClient + ClientKey también en las llamadas de datos,
        // no sólo en el login. Sin ellos, MarketData/Search responde
        // "Authorized Client was not provided." / "Client Key was not provided".
        headers.set("AuthorizedClient", authorizedClient);
        headers.set("ClientKey",        clientKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    // ─── Internal API response DTOs ───────────────────────────────────────────

    private record PpiTokenDto(
            @JsonProperty("accessToken")    String accessToken,
            @JsonProperty("expirationDate") String expirationDate
    ) {}

    /** Package-private (not private) so unit tests in the same package can build fixtures. */
    record MarketDataItem(
            @JsonProperty("date")  String     date,
            @JsonProperty("price") BigDecimal price
    ) {
        /** Parses the ISO offset date (e.g. "2026-06-26T00:00:00-03:00") to its LocalDate. */
        LocalDate dateAsLocalDate() {
            return OffsetDateTime.parse(date).toLocalDate();
        }
    }
}
