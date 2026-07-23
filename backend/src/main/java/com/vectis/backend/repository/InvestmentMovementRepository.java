package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvestmentMovementRepository extends JpaRepository<InvestmentMovement, UUID> {

    Optional<InvestmentMovement> findByIdAndInvestmentAsset_Id(UUID id, UUID investmentId);
}
