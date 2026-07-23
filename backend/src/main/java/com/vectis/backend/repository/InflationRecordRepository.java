package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InflationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InflationRecordRepository extends JpaRepository<InflationRecord, UUID> {

    Optional<InflationRecord> findTopByOrderByPeriodDateDesc();

    List<InflationRecord> findByPeriodDateBetween(LocalDate from, LocalDate to);

    boolean existsByPeriodDate(LocalDate date);
}
