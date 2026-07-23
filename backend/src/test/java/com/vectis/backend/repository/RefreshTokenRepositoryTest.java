package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.RefreshToken;
import com.vectis.backend.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());
        otherUser = em.persistFlushFind(User.builder()
                .email("other@vectis.com").passwordHash("hash").fullName("Other User").build());
    }

    @Test
    @DisplayName("deleteAllByUser: borra sólo los refresh tokens del usuario dado, no los de otro usuario")
    void deleteAllByUser_deletesOnlyOwnTokens() {
        em.persistAndFlush(RefreshToken.builder()
                .user(user).token("token-a").expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false).build());
        em.persistAndFlush(RefreshToken.builder()
                .user(user).token("token-b").expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false).build());
        em.persistAndFlush(RefreshToken.builder()
                .user(otherUser).token("token-c").expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false).build());

        refreshTokenRepository.deleteAllByUser(user);
        em.getEntityManager().flush();
        em.getEntityManager().clear();

        List<RefreshToken> remaining = refreshTokenRepository.findAll();

        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getToken()).isEqualTo("token-c");
    }
}
