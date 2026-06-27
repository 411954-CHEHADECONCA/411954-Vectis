package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.DoctaInstrumentCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctaInstrumentCacheRepository extends JpaRepository<DoctaInstrumentCache, UUID> {

    List<DoctaInstrumentCache> findAllByTipoOrderByNombreAsc(String tipo);

    Optional<DoctaInstrumentCache> findByTicker(String ticker);
}
