package com.vectis.backend.service;

import com.vectis.backend.domain.entity.RefreshToken;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.AuthResponse;
import com.vectis.backend.dto.LoginRequest;
import com.vectis.backend.dto.RegisterRequest;
import com.vectis.backend.exception.EmailAlreadyExistsException;
import com.vectis.backend.exception.InvalidCredentialsException;
import com.vectis.backend.exception.InvalidTokenException;
import com.vectis.backend.repository.RefreshTokenRepository;
import com.vectis.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604_800_000L);

        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("existing@vectis.com")
                .passwordHash("$2a$12$hashed")
                .fullName("Existing User")
                .build();
    }

    // ─── loadUserByUsername ───────────────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername retorna el usuario cuando el email existe")
    void loadUserByUsername_existingEmail_returnsUser() {
        when(userRepository.findByEmail("existing@vectis.com"))
                .thenReturn(Optional.of(existingUser));

        var result = authService.loadUserByUsername("existing@vectis.com");

        assertThat(result.getUsername()).isEqualTo("existing@vectis.com");
    }

    @Test
    @DisplayName("loadUserByUsername lanza UsernameNotFoundException cuando el email no existe")
    void loadUserByUsername_unknownEmail_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loadUserByUsername("nobody@vectis.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // ─── register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register guarda usuario con password hasheado y retorna AuthResponse")
    void register_newEmail_savesHashedPasswordAndReturnsTokens() {
        when(userRepository.existsByEmail("new@vectis.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(null);

        AuthResponse response = authService.register(
                new RegisterRequest("new@vectis.com", "password123", "New User"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("register lanza EmailAlreadyExistsException cuando el email ya está registrado")
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        when(userRepository.existsByEmail("existing@vectis.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("existing@vectis.com", "password123", "Another")))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login con credenciales correctas retorna AuthResponse con tokens")
    void login_validCredentials_returnsAuthResponse() {
        when(userRepository.findByEmail("existing@vectis.com"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "$2a$12$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.login(
                new LoginRequest("existing@vectis.com", "password123"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUser().getEmail()).isEqualTo("existing@vectis.com");
    }

    @Test
    @DisplayName("login con email inexistente lanza InvalidCredentialsException")
    void login_unknownEmail_throwsInvalidCredentialsException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("nobody@vectis.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login con contraseña incorrecta lanza InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentialsException() {
        when(userRepository.findByEmail("existing@vectis.com"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("existing@vectis.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ─── refresh ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh con token válido rota el token y retorna nuevos tokens")
    void refresh_validToken_revokesOldAndIssuesNew() {
        RefreshToken storedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("valid-refresh-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.refresh("valid-refresh-token");

        assertThat(storedToken.isRevoked()).isTrue();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("refresh con token inexistente lanza InvalidTokenException")
    void refresh_unknownToken_throwsInvalidTokenException() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("refresh con token revocado lanza InvalidTokenException")
    void refresh_revokedToken_throwsInvalidTokenException() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("revoked-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked-token"))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh con token expirado lanza InvalidTokenException")
    void refresh_expiredToken_throwsInvalidTokenException() {
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("expired-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout revoca el refresh token cuando existe")
    void logout_existingToken_revokesIt() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("my-refresh-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("my-refresh-token"))
                .thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenReturn(null);

        authService.logout("my-refresh-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("logout no falla si el token no existe")
    void logout_unknownToken_doesNotThrow() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        authService.logout("non-existent-token");

        verify(refreshTokenRepository, never()).save(any());
    }
}
