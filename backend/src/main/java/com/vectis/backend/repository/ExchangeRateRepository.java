package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findTopByRateTypeOrderByRateDateDesc(String rateType);

    boolean existsByRateTypeAndRateDate(String rateType, LocalDate date);

    Optional<ExchangeRate> findByRateTypeAndRateDate(String rateType, LocalDate date);
}
