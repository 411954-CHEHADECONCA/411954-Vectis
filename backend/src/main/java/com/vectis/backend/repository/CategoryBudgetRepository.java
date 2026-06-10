package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.CategoryBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, UUID> {

    List<CategoryBudget> findAllByUser_IdAndValidFrom(UUID userId, LocalDate validFrom);

    Optional<CategoryBudget> findByCategory_IdAndUser_IdAndValidFrom(UUID categoryId, UUID userId, LocalDate validFrom);
}
