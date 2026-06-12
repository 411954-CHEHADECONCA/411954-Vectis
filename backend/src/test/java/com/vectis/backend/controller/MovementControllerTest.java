package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.MovementRequest;
import com.vectis.backend.dto.MovementResponse;
import com.vectis.backend.dto.MovementSummaryResponse;
import com.vectis.backend.dto.PageResponse;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import com.vectis.backend.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MovementController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("MovementController")
class MovementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TransactionService transactionService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    private User mockUser;
    private UUID userId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER  = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = User.builder()
                .id(userId).email("user@vectis.com").fullName("Test User").passwordHash("hash")
                .build();

        given(jwtService.isTokenValid(VALID_TOKEN)).willReturn(true);
        given(jwtService.extractUserId(VALID_TOKEN)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    // ─── GET /api/movements ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/movements sin token retorna 401")
    void getMovements_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/movements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/movements con token retorna 200 y la página")
    void getMovements_withToken_returnsPage() throws Exception {
        PageResponse<MovementResponse> page = new PageResponse<>(
                List.of(buildResponse(UUID.randomUUID())), 0, 20, 1, 1, false);
        given(transactionService.search(eq(userId), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .willReturn(page);

        mockMvc.perform(get("/api/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Coto"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ─── GET /api/movements/summary ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/movements/summary con token retorna 200")
    void getSummary_withToken_returns200() throws Exception {
        given(transactionService.summary(eq(userId), any(), any(), any(), any(), any()))
                .willReturn(MovementSummaryResponse.builder()
                        .totalIncome(new BigDecimal("1000")).totalExpense(new BigDecimal("400"))
                        .net(new BigDecimal("600")).count(2).build());

        mockMvc.perform(get("/api/movements/summary")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.net").value(600));
    }

    // ─── POST /api/movements ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/movements con cuotas retorna 201 y la lista")
    void create_installments_returns201() throws Exception {
        MovementRequest req = new MovementRequest(
                "Notebook", new BigDecimal("60000"), "ARS", "EXPENSE",
                null, null, UUID.randomUUID(), LocalDate.of(2026, 4, 7), 3);
        given(transactionService.create(any(MovementRequest.class), any(User.class)))
                .willReturn(List.of(buildResponse(UUID.randomUUID()), buildResponse(UUID.randomUUID())));

        mockMvc.perform(post("/api/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].description").value("Coto"));
    }

    @Test
    @DisplayName("POST /api/movements con amount=0 retorna 400")
    void create_zeroAmount_returns400() throws Exception {
        MovementRequest req = new MovementRequest(
                "Coto", BigDecimal.ZERO, "ARS", "EXPENSE", null, null, null, LocalDate.of(2026, 6, 10), 1);

        mockMvc.perform(post("/api/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/movements con cuenta y tarjeta juntas retorna 400")
    void create_bothAccountAndCard_returns400() throws Exception {
        MovementRequest req = new MovementRequest(
                "Coto", new BigDecimal("1000"), "ARS", "EXPENSE",
                null, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 6, 10), 1);
        given(transactionService.create(any(MovementRequest.class), any(User.class)))
                .willThrow(new VectisException("No podés asociar cuenta y tarjeta al mismo tiempo", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /api/movements/{id} ──────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/movements/{id} con token retorna 204")
    void delete_withToken_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/movements/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private MovementResponse buildResponse(UUID id) {
        return MovementResponse.builder()
                .id(id).type("EXPENSE").description("Coto")
                .amount(new BigDecimal("86400.0000")).ccy("ARS")
                .transactionDate(LocalDate.of(2026, 6, 9)).dueDate(LocalDate.of(2026, 6, 9))
                .installment(false).createdAt(OffsetDateTime.now())
                .build();
    }
}
