package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AccountRequest;
import com.vectis.backend.dto.AccountResponse;
import com.vectis.backend.exception.AccountNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.AccountService;
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

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountController")
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AccountService accountService;
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

    // ─── GET /api/accounts ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/accounts sin token retorna 401")
    void getAccounts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/accounts con token retorna 200 y la lista")
    void getAccounts_withToken_returns200WithList() throws Exception {
        AccountResponse response = buildResponse(UUID.randomUUID());
        given(accountService.getAccounts(userId)).willReturn(List.of(response));

        mockMvc.perform(get("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cuenta Test"))
                .andExpect(jsonPath("$[0].kind").value("Banco"));
    }

    // ─── POST /api/accounts ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/accounts con body válido retorna 201")
    void createAccount_validRequest_returns201() throws Exception {
        AccountRequest request = buildRequest(false, null);
        AccountResponse response = buildResponse(UUID.randomUUID());

        given(accountService.createAccount(any(AccountRequest.class), any(User.class))).willReturn(response);

        mockMvc.perform(post("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cuenta Test"))
                .andExpect(jsonPath("$.remunerada").value(false));
    }

    @Test
    @DisplayName("POST /api/accounts con nombre en blanco retorna 400")
    void createAccount_blankName_returns400() throws Exception {
        AccountRequest request = new AccountRequest("", "Banco", null, "ARS",
                BigDecimal.ZERO, false, null);

        mockMvc.perform(post("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/accounts remunerada=true sin TNA retorna 400")
    void createAccount_remuneradaTrueTnaNull_returns400() throws Exception {
        AccountRequest request = new AccountRequest("Mi Cuenta", "Banco", null, "ARS",
                new BigDecimal("100000"), true, null);

        mockMvc.perform(post("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /api/accounts/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/accounts/{id} de cuenta de otro usuario retorna 403")
    void updateAccount_otherUserAccount_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        given(accountService.updateAccount(eq(id), any(AccountRequest.class), any(User.class)))
                .willThrow(new VectisException("No permitido", HttpStatus.FORBIDDEN));

        mockMvc.perform(put("/api/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(false, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/accounts/{id} de cuenta inexistente retorna 404")
    void updateAccount_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(accountService.updateAccount(eq(id), any(AccountRequest.class), any(User.class)))
                .willThrow(new AccountNotFoundException(id));

        mockMvc.perform(put("/api/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(false, null))))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/accounts/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/accounts/{id} sin token retorna 401")
    void deleteAccount_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/accounts/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/accounts/{id} propia retorna 204")
    void deleteAccount_ownAccount_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(accountService).deleteAccount(eq(id), any(User.class));

        mockMvc.perform(delete("/api/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/accounts/{id} de cuenta inexistente retorna 404")
    void deleteAccount_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AccountNotFoundException(id))
                .when(accountService).deleteAccount(eq(id), any(User.class));

        mockMvc.perform(delete("/api/accounts/" + id)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AccountRequest buildRequest(boolean remunerada, BigDecimal tna) {
        return new AccountRequest("Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), remunerada, tna);
    }

    private AccountResponse buildResponse(UUID id) {
        return new AccountResponse(id, "Cuenta Test", "Banco", "Caja de Ahorro $", "ARS",
                new BigDecimal("150000.0000"), false, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
