package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.RecurringMovementRequest;
import com.vectis.backend.dto.RecurringMovementResponse;
import com.vectis.backend.exception.RecurringMovementNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import com.vectis.backend.service.RecurringMovementService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecurringMovementController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("RecurringMovementController")
class RecurringMovementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RecurringMovementService recurringMovementService;
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

    // ─── GET /api/recurring-movements ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/recurring-movements sin token retorna 401")
    void getAll_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/recurring-movements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/recurring-movements con token retorna 200 y la lista")
    void getAll_withToken_returns200() throws Exception {
        RecurringMovementResponse response = buildResponse(UUID.randomUUID());
        given(recurringMovementService.getRecurringMovements(userId)).willReturn(List.of(response));

        mockMvc.perform(get("/api/recurring-movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Netflix"));
    }

    // ─── POST /api/recurring-movements ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/recurring-movements con body válido retorna 201")
    void create_validRequest_returns201() throws Exception {
        RecurringMovementRequest request = buildRequest();
        RecurringMovementResponse response = buildResponse(UUID.randomUUID());

        given(recurringMovementService.createRecurringMovement(
                any(RecurringMovementRequest.class), any(User.class))).willReturn(response);

        mockMvc.perform(post("/api/recurring-movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Netflix"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/recurring-movements con dayOfMonth=35 retorna 400")
    void create_invalidDayOfMonth_returns400() throws Exception {
        RecurringMovementRequest request = new RecurringMovementRequest(
                "Netflix", new BigDecimal("15000"), "ARS", "EXPENSE", null, null, 35);

        mockMvc.perform(post("/api/recurring-movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/recurring-movements con amount=0 retorna 400")
    void create_zeroAmount_returns400() throws Exception {
        RecurringMovementRequest request = new RecurringMovementRequest(
                "Netflix", BigDecimal.ZERO, "ARS", "EXPENSE", null, null, 10);

        mockMvc.perform(post("/api/recurring-movements")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /api/recurring-movements/{id}/toggle ───────────────────────────

    @Test
    @DisplayName("PATCH /{id}/toggle con token retorna 200")
    void toggle_withToken_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        RecurringMovementResponse response = buildResponse(id);
        given(recurringMovementService.toggleActive(eq(id), any(User.class))).willReturn(response);

        mockMvc.perform(patch("/api/recurring-movements/" + id + "/toggle")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /{id}/toggle de movimiento inexistente retorna 404")
    void toggle_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(recurringMovementService.toggleActive(eq(id), any(User.class)))
                .willThrow(new RecurringMovementNotFoundException(id));

        mockMvc.perform(patch("/api/recurring-movements/" + id + "/toggle")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/recurring-movements/{id} ────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} con token retorna 204")
    void delete_withToken_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(recurringMovementService).deleteRecurringMovement(eq(id), any(User.class));

        mockMvc.perform(delete("/api/recurring-movements/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{id} de movimiento de otro usuario retorna 403")
    void delete_forbidden_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new VectisException("No permitido", HttpStatus.FORBIDDEN))
                .when(recurringMovementService).deleteRecurringMovement(eq(id), any(User.class));

        mockMvc.perform(delete("/api/recurring-movements/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private RecurringMovementRequest buildRequest() {
        return new RecurringMovementRequest(
                "Netflix", new BigDecimal("15000.0000"), "ARS", "EXPENSE", null, null, 10);
    }

    private RecurringMovementResponse buildResponse(UUID id) {
        return new RecurringMovementResponse(
                id, "Netflix", new BigDecimal("15000.0000"), "ARS", "EXPENSE",
                null, null, null, null, null, null,
                10, true, OffsetDateTime.now());
    }
}
