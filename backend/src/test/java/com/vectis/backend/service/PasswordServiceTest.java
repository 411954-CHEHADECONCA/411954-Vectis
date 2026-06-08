package com.vectis.backend.service;

import com.vectis.backend.domain.entity.PasswordResetToken;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.exception.InvalidCredentialsException;
import com.vectis.backend.exception.InvalidTokenException;
import com.vectis.backend.repository.PasswordResetTokenRepository;
import com.vectis.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
@DisplayName("PasswordService")
class PasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;

    @InjectMocks
    private PasswordService passwordService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordService, "mailFrom", "noreply@vectis.app");
        ReflectionTestUtils.setField(passwordService, "frontendUrl", "http://localhost:4200");

        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@vectis.com")
                .passwordHash("$2a$12$hashed")
                .fullName("Test User")
                .build();
    }

    // ─── forgotPassword ──────────────────────────────────────────────────────

    @Test
    @DisplayName("forgotPassword con email conocido → elimina tokens anteriores y envía email")
    void forgotPassword_knownEmail_deletesOldTokensAndSendsEmail() {
        when(userRepository.findByEmail("user@vectis.com")).thenReturn(Optional.of(existingUser));
        when(resetTokenRepository.save(any())).thenReturn(null);

        passwordService.forgotPassword("user@vectis.com");

        verify(resetTokenRepository).deleteByUserAndUsedFalse(existingUser);
        verify(resetTokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getTo()).contains("user@vectis.com");
    }

    @Test
    @DisplayName("forgotPassword con email desconocido → no lanza excepción ni envía email")
    void forgotPassword_unknownEmail_doesNotThrowAndDoesNotSendEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        passwordService.forgotPassword("nobody@vectis.com");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(resetTokenRepository, never()).save(any());
    }

    // ─── resetPassword ───────────────────────────────────────────────────────

    @Test
    @DisplayName("resetPassword con token válido → actualiza la contraseña y marca token como usado")
    void resetPassword_validToken_updatesPasswordAndMarksUsed() {
        PasswordResetToken prt = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("valid-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .used(false)
                .build();

        when(resetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(prt));
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$12$newHashed");
        when(userRepository.save(any())).thenReturn(existingUser);
        when(resetTokenRepository.save(any())).thenReturn(null);

        passwordService.resetPassword("valid-token", "newPassword123");

        assertThat(existingUser.getPasswordHash()).isEqualTo("$2a$12$newHashed");
        assertThat(prt.isUsed()).isTrue();
        verify(userRepository).save(existingUser);
        verify(resetTokenRepository).save(prt);
    }

    @Test
    @DisplayName("resetPassword con token no encontrado → lanza InvalidTokenException")
    void resetPassword_unknownToken_throwsInvalidTokenException() {
        when(resetTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.resetPassword("bad-token", "newPassword123"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    @DisplayName("resetPassword con token expirado → lanza InvalidTokenException")
    void resetPassword_expiredToken_throwsInvalidTokenException() {
        PasswordResetToken prt = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("expired-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
                .used(false)
                .build();

        when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> passwordService.resetPassword("expired-token", "newPassword123"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("resetPassword con token ya usado → lanza InvalidTokenException")
    void resetPassword_usedToken_throwsInvalidTokenException() {
        PasswordResetToken prt = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(existingUser)
                .token("used-token")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .used(true)
                .build();

        when(resetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> passwordService.resetPassword("used-token", "newPassword123"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("utilizado");
    }

    // ─── changePassword ──────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword con contraseña actual correcta → actualiza el hash")
    void changePassword_correctCurrentPassword_updatesHash() {
        when(userRepository.findById(existingUser.getId())).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("currentPass", "$2a$12$hashed")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("$2a$12$newHashed");
        when(userRepository.save(any())).thenReturn(existingUser);

        passwordService.changePassword(existingUser.getId(), "currentPass", "newPass123");

        assertThat(existingUser.getPasswordHash()).isEqualTo("$2a$12$newHashed");
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("changePassword con contraseña actual incorrecta → lanza InvalidCredentialsException")
    void changePassword_wrongCurrentPassword_throwsInvalidCredentialsException() {
        when(userRepository.findById(existingUser.getId())).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPass", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                passwordService.changePassword(existingUser.getId(), "wrongPass", "newPass123"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }
}
