package com.vectis.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.dto.AuthResponse;
import com.vectis.backend.dto.LoginRequest;
import com.vectis.backend.dto.RegisterRequest;
import com.vectis.backend.exception.EmailAlreadyExistsException;
import com.vectis.backend.exception.InvalidCredentialsException;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.AuthService;
import com.vectis.backend.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /register con body válido retorna 201 y tokens")
    void register_validRequest_returns201WithTokens() throws Exception {
        AuthResponse mockResponse = buildMockAuthResponse();
        when(authService.register(any(RegisterRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("new@vectis.com", "password123", "New User"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.user.email").value("new@vectis.com"));
    }

    @Test
    @DisplayName("POST /register con email mal formateado retorna 400")
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("not-an-email", "password123", "User"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register con contraseña demasiado corta retorna 400")
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("user@vectis.com", "short", "User"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register con email duplicado retorna 409")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("dup@vectis.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("dup@vectis.com", "password123", "User"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login con credenciales correctas retorna 200 y tokens")
    void login_validCredentials_returns200WithTokens() throws Exception {
        AuthResponse mockResponse = buildMockAuthResponse();
        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("user@vectis.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("POST /login con credenciales incorrectas retorna 401")
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("user@vectis.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /login con body vacío retorna 400")
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/auth/refresh ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /refresh con token válido retorna 200 y nuevos tokens")
    void refresh_validToken_returns200() throws Exception {
        AuthResponse mockResponse = buildMockAuthResponse();
        when(authService.refresh("valid-refresh-token")).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    // ─── POST /api/auth/logout ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout retorna 204 No Content")
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some-refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    // ─── Protección de endpoints ──────────────────────────────────────────────

    @Test
    @DisplayName("GET en endpoint protegido sin token retorna 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/some-protected-resource"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private AuthResponse buildMockAuthResponse() {
        return AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .user(AuthResponse.UserInfo.builder()
                        .id(UUID.randomUUID())
                        .email("new@vectis.com")
                        .fullName("New User")
                        .build())
                .build();
    }
}
