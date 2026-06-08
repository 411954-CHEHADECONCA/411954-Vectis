package com.vectis.backend.service;

import com.vectis.backend.domain.entity.PasswordResetToken;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.exception.InvalidCredentialsException;
import com.vectis.backend.exception.InvalidTokenException;
import com.vectis.backend.repository.PasswordResetTokenRepository;
import com.vectis.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            resetTokenRepository.deleteByUserAndUsedFalse(user);

            String raw = UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken prt = PasswordResetToken.builder()
                    .user(user)
                    .token(raw)
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                    .build();
            resetTokenRepository.save(prt);

            sendResetEmail(user, raw);
        });
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Token inválido"));

        if (prt.isExpired()) {
            throw new InvalidTokenException("Token expirado");
        }
        if (prt.isUsed()) {
            throw new InvalidTokenException("Token ya utilizado");
        }

        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsed(true);
        resetTokenRepository.save(prt);
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void sendResetEmail(User user, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Vectis — Recuperación de contraseña");
        message.setText(
            "Hola " + user.getFullName() + ",\n\n" +
            "Recibiste este email porque solicitaste restablecer tu contraseña.\n\n" +
            "Hacé clic en el siguiente enlace (válido por 1 hora):\n" +
            resetLink + "\n\n" +
            "Si no solicitaste este cambio, ignorá este mensaje.\n\n" +
            "— Vectis Finance"
        );

        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.warn("Could not send reset email to {}. Reset link: {}", user.getEmail(), resetLink, e);
        }
    }
}
