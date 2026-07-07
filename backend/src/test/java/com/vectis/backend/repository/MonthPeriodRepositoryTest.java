package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.MonthPeriod;
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
class MonthPeriodRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MonthPeriodRepository monthPeriodRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(User.builder()
                .email("user@vectis.com").passwordHash("hash").fullName("Test User").build());
    }

    private MonthPeriod.MonthPeriodBuilder basePeriod(int year, int month, String status) {
        return MonthPeriod.builder().user(user).year(year).month(month).status(status)
                .openedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("findAllExpiredOpen: sólo períodos OPEN estrictamente anteriores a (año, mes) dados")
    void findAllExpiredOpen_returnsOnlyOpenBeforeGivenMonth() {
        em.persistAndFlush(basePeriod(2026, 5, "OPEN").build());   // año igual, mes anterior → expira
        em.persistAndFlush(basePeriod(2025, 12, "OPEN").build());  // año anterior → expira
        em.persistAndFlush(basePeriod(2026, 6, "OPEN").build());   // mismo mes, no es anterior → no expira
        em.persistAndFlush(basePeriod(2026, 4, "CLOSED").build()); // anterior pero ya cerrado → no debe salir

        List<MonthPeriod> result = monthPeriodRepository.findAllExpiredOpen(2026, 6);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MonthPeriod::getStatus).containsOnly("OPEN");
        assertThat(result).extracting(mp -> mp.getYear() * 100 + mp.getMonth())
                .containsExactlyInAnyOrder(202605, 202512);
    }

    @Test
    @DisplayName("findAllExpiredOpen: un período OPEN del mismo año pero mes posterior no se considera expirado")
    void findAllExpiredOpen_excludesFutureMonthSameYear() {
        em.persistAndFlush(basePeriod(2026, 8, "OPEN").build());

        List<MonthPeriod> result = monthPeriodRepository.findAllExpiredOpen(2026, 6);

        assertThat(result).isEmpty();
    }
}
