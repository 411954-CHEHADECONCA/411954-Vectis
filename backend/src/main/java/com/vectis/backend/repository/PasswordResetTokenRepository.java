package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.PasswordResetToken;
import com.vectis.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUserAndUsedFalse(User user);
}
