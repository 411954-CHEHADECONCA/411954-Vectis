package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.FciVcpSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FciVcpSnapshotRepository#findLatestSnapshotDate()} es {@code nativeQuery = true} (SQL crudo,
 * no JPQL) — ejecutarla acá confirma que el nombre de tabla/columna generado por Hibernate
 * ({@code create-drop} desde la entidad) coincide con lo que esta query nativa espera a mano.
 *
 * <p>Ya no necesita su propio contexto/base H2 aislado: el perfil de test general dejó de activar
 * {@code globally_quoted_identifiers}, ya que las únicas columnas que lo necesitaban
 * ({@code MonthPeriod.year}/{@code month}, built-ins de H2) ahora se citan puntualmente con
 * {@code @Column(name = "`year`")}. Con eso corre en la misma base {@code vectis_test} que el resto
 * de los {@code @DataJpaTest}, igual que cualquier query nativa futura.
 */
@DataJpaTest
@ActiveProfiles("test")
class FciVcpSnapshotRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private FciVcpSnapshotRepository fciVcpSnapshotRepository;

    private FciVcpSnapshot.FciVcpSnapshotBuilder baseSnapshot(LocalDate fecha) {
        return FciVcpSnapshot.builder()
                .fondo("Pionero Ahorro Pesos").categoria("MONEY_MARKET")
                .vcp(new BigDecimal("15.4321")).fecha(fecha);
    }

    @Test
    @DisplayName("findLatestSnapshotDate: devuelve la fecha más reciente entre todos los snapshots")
    void findLatestSnapshotDate_returnsMostRecentDate() {
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 1)).build());
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 15)).build());
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 6, 20)).build());

        Optional<LocalDate> result = fciVcpSnapshotRepository.findLatestSnapshotDate();

        assertThat(result).contains(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("findLatestSnapshotDate: vacío si no hay ningún snapshot cargado")
    void findLatestSnapshotDate_emptyWhenNoSnapshots() {
        Optional<LocalDate> result = fciVcpSnapshotRepository.findLatestSnapshotDate();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findTopByFondoAndFechaLessThanEqualOrderByFechaDesc: devuelve el snapshot más reciente ≤ fecha")
    void findTopByFondoAndFechaLessThanEqualOrderByFechaDesc_returnsMostRecentBeforeOrEqual() {
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 1)).build());
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 15)).build());
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 21)).build());
        // fecha posterior a la buscada, no debe influir
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 30)).build());

        Optional<FciVcpSnapshot> result = fciVcpSnapshotRepository
                .findTopByFondoAndFechaLessThanEqualOrderByFechaDesc("Pionero Ahorro Pesos", LocalDate.of(2026, 7, 24));

        assertThat(result).isPresent();
        assertThat(result.get().getFecha()).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Test
    @DisplayName("findTopByFondoAndFechaLessThanEqualOrderByFechaDesc: vacío si no hay snapshots ≤ fecha")
    void findTopByFondoAndFechaLessThanEqualOrderByFechaDesc_emptyWhenNoneBeforeOrEqual() {
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 30)).build());

        Optional<FciVcpSnapshot> result = fciVcpSnapshotRepository
                .findTopByFondoAndFechaLessThanEqualOrderByFechaDesc("Pionero Ahorro Pesos", LocalDate.of(2026, 7, 24));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findTopByFondoAndFechaLessThanEqualOrderByFechaDesc: vacío si el fondo no coincide")
    void findTopByFondoAndFechaLessThanEqualOrderByFechaDesc_emptyWhenFondoMismatch() {
        em.persistAndFlush(baseSnapshot(LocalDate.of(2026, 7, 1)).build());

        Optional<FciVcpSnapshot> result = fciVcpSnapshotRepository
                .findTopByFondoAndFechaLessThanEqualOrderByFechaDesc("Otro Fondo", LocalDate.of(2026, 7, 24));

        assertThat(result).isEmpty();
    }
}
