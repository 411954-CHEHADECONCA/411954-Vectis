package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentValuationRepository extends JpaRepository<InvestmentValuation, UUID> {

    Optional<InvestmentValuation> findByIdAndInvestmentAsset_Id(UUID id, UUID investmentId);

    boolean existsByInvestmentAsset_IdAndValuationDate(UUID investmentId, LocalDate valuationDate);

    /** Usado por los syncs de mercado para poder pisar valuaciones auto-generadas existentes. */
    Optional<InvestmentValuation> findByInvestmentAsset_IdAndValuationDate(UUID investmentId, LocalDate valuationDate);

    /** Usado por MarketDataRefreshService para chequear frescura de precios PPI (LETRA/BONO/ON). */
    boolean existsBySourceAndValuationDate(String source, LocalDate valuationDate);

    /** Última fecha con valuación de una fuente dada — usado para exponer "último sync" en el status de mercado. */
    @Query("SELECT MAX(v.valuationDate) FROM InvestmentValuation v WHERE v.source = :source")
    Optional<LocalDate> findMaxValuationDateBySource(@Param("source") String source);
}
