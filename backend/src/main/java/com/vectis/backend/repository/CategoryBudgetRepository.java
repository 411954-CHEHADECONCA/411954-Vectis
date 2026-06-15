package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.CategoryBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, UUID> {

    List<CategoryBudget> findAllByUser_IdAndValidFrom(UUID userId, LocalDate validFrom);

    /**
     * Igual que el método derivado pero con JOIN FETCH en category para evitar N+1.
     * Usar este método en CashflowService.loadBudgets() donde se accede a cb.getCategory().getId().
     */
    @Query("SELECT cb FROM CategoryBudget cb JOIN FETCH cb.category WHERE cb.user.id = :userId AND cb.validFrom = :validFrom")
    List<CategoryBudget> findAllByUser_IdAndValidFromEager(
            @Param("userId") UUID userId,
            @Param("validFrom") LocalDate validFrom);

    Optional<CategoryBudget> findByCategory_IdAndUser_IdAndValidFrom(UUID categoryId, UUID userId, LocalDate validFrom);
}
