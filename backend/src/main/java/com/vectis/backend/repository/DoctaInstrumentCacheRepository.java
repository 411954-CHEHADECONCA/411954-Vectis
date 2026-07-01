package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.DoctaInstrumentCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctaInstrumentCacheRepository extends JpaRepository<DoctaInstrumentCache, UUID> {

    /** Sólo instrumentos vigentes (active=true) — alimenta el selector del front. */
    List<DoctaInstrumentCache> findAllByTipoAndActiveTrueOrderByNombreAsc(String tipo);

    /** Todos los vigentes (sin filtrar por tipo) para el selector. */
    List<DoctaInstrumentCache> findAllByActiveTrueOrderByNombreAsc();

    Optional<DoctaInstrumentCache> findByTicker(String ticker);

    /** Instrumentos vigentes de los tipos indicados — usado por el sync de catálogo para validar. */
    List<DoctaInstrumentCache> findAllByTipoInAndActiveTrue(List<String> tipos);
}
