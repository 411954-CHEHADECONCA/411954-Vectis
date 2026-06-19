package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.MonthPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthPeriodRepository extends JpaRepository<MonthPeriod, UUID> {

    Optional<MonthPeriod> findByUser_IdAndYearAndMonth(UUID userId, int year, int month);

    /**
     * All OPEN periods that belong to a month strictly before (y, m).
     * Used by the auto-close scheduler to find expired open periods.
     */
    @Query("""
            SELECT mp FROM MonthPeriod mp
            WHERE mp.status = 'OPEN'
              AND (mp.year < :y OR (mp.year = :y AND mp.month < :m))
            """)
    List<MonthPeriod> findAllExpiredOpen(@Param("y") int y, @Param("m") int m);

    /** Used by the monthly open scheduler to iterate across all users. */
    List<MonthPeriod> findByYearAndMonth(int year, int month);
}
