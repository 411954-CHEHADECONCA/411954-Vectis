package com.vectis.backend.controller;

import com.vectis.backend.config.SecurityConfig;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.repository.UserRepository;
import com.vectis.backend.service.JwtService;
import com.vectis.backend.service.PasswordService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("PasswordController")
class PasswordControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PasswordService passwordService;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    // ─── POST /api/auth/forgot-password ─────────────────────────────────────

    @Test
    @DisplayName("POST /forgot-password con email válido → 204 No Content")
    void forgotPassword_validEmail_returns204() throws Exception {
        doNothing().when(passwordService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@vectis.com\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /forgot-password con email mal formateado → 400")
    void forgotPassword_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/auth/reset-password ──────────────────────────────────────

    @Test
    @DisplayName("POST /reset-password con datos válidos → 204 No Content")
    void resetPassword_validRequest_returns204() throws Exception {
        doNothing().when(passwordService).resetPassword(any(), any());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc123\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isNoContent());
    }

    // ─── PATCH /api/users/me/password ────────────────────────────────────────

    @Test
    @DisplayName("PATCH /users/me/password sin autenticación → 401")
    void changePassword_withoutAuth_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /users/me/password autenticado con datos válidos → 204")
    void changePassword_authenticated_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .passwordHash("$2a$12$hashed")
                .fullName("Test User")
                .build();

        doNothing().when(passwordService).changePassword(eq(userId), any(), any());

        mockMvc.perform(patch("/api/users/me/password")
                        .with(user(mockUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"currentPass\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isNoContent());

        verify(passwordService).changePassword(eq(userId), eq("currentPass"), eq("newPass123"));
    }
}
