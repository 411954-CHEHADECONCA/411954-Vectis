package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndDeletedAtIsNull(UUID id);

    List<Transaction> findAllByInstallmentGroupIdAndDeletedAtIsNull(UUID installmentGroupId);

    /** Listado paginado con filtros opcionales. EntityGraph evita N+1 al traer las relaciones. */
    @EntityGraph(attributePaths = {"category", "account", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.dueDate BETWEEN :from AND :to
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY t.dueDate DESC, t.createdAt DESC
        """)
    Page<Transaction> search(@Param("userId") UUID userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("type") String type,
                             @Param("categoryId") UUID categoryId,
                             @Param("q") String q,
                             Pageable pageable);

    /** Suma de montos por tipo dentro del período/filtros (para el resumen). */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.type = :type
          AND t.dueDate BETWEEN :from AND :to
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        """)
    BigDecimal sumByType(@Param("userId") UUID userId,
                         @Param("type") String type,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryId") UUID categoryId,
                         @Param("q") String q);

    /** Cantidad de movimientos del período/filtros (para el resumen). */
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.dueDate BETWEEN :from AND :to
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        """)
    long countFiltered(@Param("userId") UUID userId,
                       @Param("from") LocalDate from,
                       @Param("to") LocalDate to,
                       @Param("type") String type,
                       @Param("categoryId") UUID categoryId,
                       @Param("q") String q);
}
