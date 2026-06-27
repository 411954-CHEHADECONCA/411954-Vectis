package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentValuation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentValuationRepository extends JpaRepository<InvestmentValuation, UUID> {

    Optional<InvestmentValuation> findByIdAndInvestmentAsset_Id(UUID id, UUID investmentId);

    boolean existsByInvestmentAsset_IdAndValuationDate(UUID investmentId, LocalDate valuationDate);
}
