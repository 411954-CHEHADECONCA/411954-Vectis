package com.vectis.backend.service;

import com.vectis.backend.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService")
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-chars-long!!";
    private static final long EXPIRATION_MS = 900_000L;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@vectis.com")
                .passwordHash("hashed")
                .fullName("Test User")
                .build();
    }

    @Test
    @DisplayName("Token generado contiene el userId del usuario como subject")
    void generateToken_containsUserIdAsSubject() {
        String token = jwtService.generateAccessToken(testUser);

        String extractedId = jwtService.extractUserId(token);

        assertThat(extractedId).isEqualTo(testUser.getId().toString());
    }

    @Test
    @DisplayName("Token válido es reconocido como válido")
    void isTokenValid_withValidToken_returnsTrue() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("Token expirado es rechazado")
    void isTokenValid_withExpiredToken_returnsFalse() {
        // Generar token con expiración negativa (ya vencido)
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1_000L);
        String expiredToken = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Token manipulado (firma inválida) es rechazado")
    void isTokenValid_withTamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("String vacío es rechazado como token inválido")
    void isTokenValid_withEmptyString_returnsFalse() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("String basura es rechazado como token inválido")
    void isTokenValid_withGarbageString_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.token")).isFalse();
    }
}
