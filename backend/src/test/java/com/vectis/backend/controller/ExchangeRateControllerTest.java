package com.vectis.backend.controller;

import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ExchangeRateResponse;
import com.vectis.backend.dto.InflationResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import com.vectis.backend.service.MacroDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeRateController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("ExchangeRateController")
class ExchangeRateControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private MacroDataService macroDataService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER  = "Bearer " + VALID_TOKEN;

    private final ExchangeRateResponse OFICIAL_RESPONSE = new ExchangeRateResponse(
        "OFICIAL", "1060.0000", "1062.5000", "2026-06-22", "dolarapi.com"
    );

    private final ExchangeRateResponse MEP_RESPONSE = new ExchangeRateResponse(
        "MEP", "1200.0000", "1202.0000", "2026-06-22", "argentinadatos.com"
    );

    private final InflationResponse IPC_RESPONSE = new InflationResponse(
        "2.4000", "2026-05-31", "argentinadatos.com"
    );

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        User mockUser = User.builder()
            .id(userId).email("user@vectis.com")
            .fullName("Test User").passwordHash("hash")
            .build();

        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    // ─── GET /api/exchange-rates/oficial/latest ───────────────────────────────

    @Test
    @DisplayName("GET /api/exchange-rates/oficial/latest sin token retorna 401")
    void getLatestOficialRate_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/oficial/latest"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/exchange-rates/oficial/latest con token retorna 200 y cotizacion oficial")
    void getLatestOficialRate_withToken_returns200() throws Exception {
        given(macroDataService.getLatestOficialRate()).willReturn(OFICIAL_RESPONSE);

        mockMvc.perform(get("/api/exchange-rates/oficial/latest")
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rateType").value("OFICIAL"))
            .andExpect(jsonPath("$.sell").value("1062.5000"))
            .andExpect(jsonPath("$.rateDate").value("2026-06-22"))
            .andExpect(jsonPath("$.source").value("dolarapi.com"));
    }

    @Test
    @DisplayName("GET /api/exchange-rates/oficial/latest retorna 404 cuando no hay datos")
    void getLatestOficialRate_notFound_returns404() throws Exception {
        given(macroDataService.getLatestOficialRate())
            .willThrow(new VectisException("Cotizacion oficial no disponible", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/exchange-rates/oficial/latest")
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Cotizacion oficial no disponible"));
    }

    // ─── GET /api/exchange-rates/mep/latest ──────────────────────────────────

    @Test
    @DisplayName("GET /api/exchange-rates/mep/latest sin token retorna 401")
    void getLatestMepRate_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/mep/latest"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/exchange-rates/mep/latest con token retorna 200 y cotizacion MEP")
    void getLatestMepRate_withToken_returns200() throws Exception {
        given(macroDataService.getLatestMepRate()).willReturn(MEP_RESPONSE);

        mockMvc.perform(get("/api/exchange-rates/mep/latest")
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rateType").value("MEP"))
            .andExpect(jsonPath("$.sell").value("1202.0000"))
            .andExpect(jsonPath("$.source").value("argentinadatos.com"));
    }

    // ─── GET /api/inflation/latest ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/inflation/latest sin token retorna 401")
    void getLatestInflation_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/inflation/latest"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/inflation/latest con token retorna 200 y datos IPC")
    void getLatestInflation_withToken_returns200() throws Exception {
        given(macroDataService.getLatestInflation()).willReturn(IPC_RESPONSE);

        mockMvc.perform(get("/api/inflation/latest")
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyRate").value("2.4000"))
            .andExpect(jsonPath("$.periodDate").value("2026-05-31"));
    }

    @Test
    @DisplayName("GET /api/inflation/latest retorna 404 cuando no hay datos")
    void getLatestInflation_notFound_returns404() throws Exception {
        given(macroDataService.getLatestInflation())
            .willThrow(new VectisException("IPC no disponible", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/inflation/latest")
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("IPC no disponible"));
    }
}
