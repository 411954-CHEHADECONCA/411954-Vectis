package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CardRequest;
import com.vectis.backend.dto.CardResponse;
import com.vectis.backend.exception.CreditCardNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.CreditCardService;
import com.vectis.backend.service.JwtService;
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

@WebMvcTest(CreditCardController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("CreditCardController")
class CreditCardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreditCardService creditCardService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    private User mockUser;
    private UUID userId;
    private static final String VALID_TOKEN = "valid-test-token";
    private static final String AUTH_HEADER = "Bearer " + VALID_TOKEN;

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

    // ─── GET /api/cards ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cards sin token retorna 401")
    void getCards_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/cards con token retorna 200 y la lista")
    void getCards_withToken_returns200WithList() throws Exception {
        CardResponse response = buildResponse(UUID.randomUUID());
        given(creditCardService.getCards(userId)).willReturn(List.of(response));

        mockMvc.perform(get("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bank").value("Galicia"))
                .andExpect(jsonPath("$[0].network").value("Visa"));
    }

    // ─── POST /api/cards ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/cards con body válido retorna 201")
    void createCard_validRequest_returns201() throws Exception {
        CardRequest request = buildRequest();
        CardResponse response = buildResponse(UUID.randomUUID());

        given(creditCardService.createCard(any(CardRequest.class), any(User.class))).willReturn(response);

        mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bank").value("Galicia"))
                .andExpect(jsonPath("$.closingDay").value(15));
    }

    @Test
    @DisplayName("POST /api/cards con banco en blanco retorna 400")
    void createCard_blankBank_returns400() throws Exception {
        CardRequest request = new CardRequest("", "Visa", "1234", "ARS",
                new BigDecimal("500000"), 15, 5, "#52eacd");

        mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/cards con last4 no numérico retorna 400")
    void createCard_invalidLast4_returns400() throws Exception {
        CardRequest request = new CardRequest("Galicia", "Visa", "abcd", "ARS",
                new BigDecimal("500000"), 15, 5, "#52eacd");

        mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/cards con closingDay=0 retorna 400")
    void createCard_closingDayZero_returns400() throws Exception {
        CardRequest request = new CardRequest("Galicia", "Visa", "1234", "ARS",
                new BigDecimal("500000"), 0, 5, "#52eacd");

        mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/cards con closingDay=32 retorna 400")
    void createCard_closingDay32_returns400() throws Exception {
        CardRequest request = new CardRequest("Galicia", "Visa", "1234", "ARS",
                new BigDecimal("500000"), 32, 5, "#52eacd");

        mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /api/cards/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/cards/{id} de tarjeta de otro usuario retorna 403")
    void updateCard_otherUserCard_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        given(creditCardService.updateCard(eq(id), any(CardRequest.class), any(User.class)))
                .willThrow(new VectisException("No permitido", HttpStatus.FORBIDDEN));

        mockMvc.perform(put("/api/cards/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/cards/{id} de tarjeta inexistente retorna 404")
    void updateCard_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(creditCardService.updateCard(eq(id), any(CardRequest.class), any(User.class)))
                .willThrow(new CreditCardNotFoundException(id));

        mockMvc.perform(put("/api/cards/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/cards/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cards/{id} sin token retorna 401")
    void deleteCard_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/cards/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/cards/{id} propia retorna 204")
    void deleteCard_ownCard_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(creditCardService).deleteCard(eq(id), any(User.class));

        mockMvc.perform(delete("/api/cards/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/cards/{id} de tarjeta inexistente retorna 404")
    void deleteCard_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CreditCardNotFoundException(id))
                .when(creditCardService).deleteCard(eq(id), any(User.class));

        mockMvc.perform(delete("/api/cards/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CardRequest buildRequest() {
        return new CardRequest("Galicia", "Visa", "1234", "ARS",
                new BigDecimal("500000.0000"), 15, 5, "#52eacd");
    }

    private CardResponse buildResponse(UUID id) {
        return new CardResponse(id, "Galicia", "Visa", "1234", "ARS",
                new BigDecimal("500000.0000"), 15, 5, "#52eacd",
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
