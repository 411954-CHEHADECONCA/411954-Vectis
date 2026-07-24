package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.FciVcpSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FciVcpSnapshotRepository extends JpaRepository<FciVcpSnapshot, UUID> {

    boolean existsByFondoAndFecha(String fondo, LocalDate fecha);

    Optional<FciVcpSnapshot> findTopByFondoOrderByFechaDesc(String fondo);

    Optional<FciVcpSnapshot> findByFondoAndFecha(String fondo, LocalDate fecha);

    Optional<FciVcpSnapshot> findTopByFondoAndFechaLessThanEqualOrderByFechaDesc(String fondo, LocalDate fecha);

    List<FciVcpSnapshot> findByCategoriaAndFecha(String categoria, LocalDate fecha);

    @Query(value = "SELECT MAX(fecha) FROM fci_vcp_snapshots", nativeQuery = true)
    Optional<LocalDate> findLatestSnapshotDate();
}
