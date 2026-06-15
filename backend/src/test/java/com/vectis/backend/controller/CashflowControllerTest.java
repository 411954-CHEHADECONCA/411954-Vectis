package com.vectis.backend.controller;

import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.*;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.CashflowService;
import com.vectis.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashflowController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("CashflowController")
class CashflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CashflowService cashflowService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private User mockUser;
    private UUID userId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER  = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    // ─── autenticación ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cashflow sin token retorna 401")
    void getCashflow_sinToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cashflow"))
                .andExpect(status().isUnauthorized());
    }

    // ─── mes actual ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cashflow con token retorna 200 con campos requeridos")
    void getCashflow_conToken_returns200ConCamposRequeridos() throws Exception {
        LocalDate today = LocalDate.now();
        CashflowResponse response = buildResponse(today.getYear(), today.getMonthValue(), false, "curso");

        given(cashflowService.getCashflow(any(User.class), anyInt(), anyInt()))
                .willReturn(response);

        mockMvc.perform(get("/api/cashflow")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(today.getYear()))
                .andExpect(jsonPath("$.month").value(today.getMonthValue()))
                .andExpect(jsonPath("$.status").value("curso"))
                .andExpect(jsonPath("$.isProjection").value(false))
                .andExpect(jsonPath("$.openingBalance").exists())
                .andExpect(jsonPath("$.income").exists())
                .andExpect(jsonPath("$.expenses").exists())
                .andExpect(jsonPath("$.closingBalance").exists());
    }

    // ─── mes futuro (proyección) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cashflow mes futuro retorna 200 con isProjection=true")
    void getCashflow_mesFuturo_returns200ConIsProjectionTrue() throws Exception {
        LocalDate futureMonth = LocalDate.now().plusMonths(1);
        int year  = futureMonth.getYear();
        int month = futureMonth.getMonthValue();
        CashflowResponse response = buildResponse(year, month, true, "proyectado");

        given(cashflowService.getCashflow(any(User.class), anyInt(), anyInt()))
                .willReturn(response);

        mockMvc.perform(get("/api/cashflow")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .param("year",  String.valueOf(year))
                        .param("month", String.valueOf(month)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProjection").value(true))
                .andExpect(jsonPath("$.status").value("proyectado"));
    }

    // ─── parámetros opcionales ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cashflow con year y month explícitos retorna 200")
    void getCashflow_conYearYMes_returns200() throws Exception {
        CashflowResponse response = buildResponse(2025, 3, false, "cerrado");

        given(cashflowService.getCashflow(any(User.class), anyInt(), anyInt()))
                .willReturn(response);

        mockMvc.perform(get("/api/cashflow")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .param("year",  "2025")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.month").value(3))
                .andExpect(jsonPath("$.status").value("cerrado"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private CashflowResponse buildResponse(int year, int month, boolean isProjection, String status) {
        CashflowBalanceSection emptyBalance = new CashflowBalanceSection(
                BigDecimal.ZERO, Collections.emptyList());
        CashflowFlowSection emptyFlow = new CashflowFlowSection(
                BigDecimal.ZERO, Collections.emptyList());
        CashflowSubtotal subtotal = new CashflowSubtotal(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        CashflowInvestmentSection emptyInvestments = new CashflowInvestmentSection(
                BigDecimal.ZERO, null, Collections.emptyList());

        return new CashflowResponse(
                year, month, "Período " + month + "/" + year, "mes",
                status, isProjection,
                emptyBalance,
                emptyFlow,
                emptyFlow,
                subtotal,
                emptyInvestments,
                emptyBalance
        );
    }
}
