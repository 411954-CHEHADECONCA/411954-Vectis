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
     * Exact-match – usado por CategoryService para cargar el presupuesto del mes actual en la UI de Config.
     */
    @Query("SELECT cb FROM CategoryBudget cb JOIN FETCH cb.category WHERE cb.user.id = :userId AND cb.validFrom = :validFrom")
    List<CategoryBudget> findAllByUser_IdAndValidFromEager(
            @Param("userId") UUID userId,
            @Param("validFrom") LocalDate validFrom);

    /**
     * Por cada categoría devuelve el registro de presupuesto más reciente cuyo valid_from ≤ validFrom.
     * Usado por CashflowService para respetar el historial de presupuestos: editar el presupuesto hoy
     * no altera lo que se muestra en meses cerrados anteriores.
     */
    @Query("""
            SELECT cb FROM CategoryBudget cb JOIN FETCH cb.category
            WHERE cb.user.id = :userId
            AND cb.validFrom = (
                SELECT MAX(cb2.validFrom) FROM CategoryBudget cb2
                WHERE cb2.user.id = :userId
                AND cb2.category.id = cb.category.id
                AND cb2.validFrom <= :validFrom
            )
            """)
    List<CategoryBudget> findLatestPerCategoryOnOrBefore(
            @Param("userId") UUID userId,
            @Param("validFrom") LocalDate validFrom);

    Optional<CategoryBudget> findByCategory_IdAndUser_IdAndValidFrom(UUID categoryId, UUID userId, LocalDate validFrom);
}
