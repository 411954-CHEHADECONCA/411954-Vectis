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

    /** Deuda de tarjeta vigente del usuario (pendiente + futura) para la vista de Tarjetas. */
    @EntityGraph(attributePaths = {"category", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.card IS NOT NULL
          AND t.dueDate >= :fromDate
        ORDER BY t.card.id, t.installmentGroupId, t.installmentNumber, t.dueDate
        """)
    List<Transaction> findCardDebt(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate);

    /**
     * Listado paginado con filtros opcionales. EntityGraph evita N+1 al traer las relaciones.
     * Criterio de fecha: cuotas (installment=true) por dueDate; pagos únicos por transactionDate.
     * Esto permite que una compra con tarjeta de pago único aparezca en el mes de compra,
     * mientras que cada cuota aparece en su mes de vencimiento (proyección financiera).
     */
    @EntityGraph(attributePaths = {"category", "account", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND ((t.installment = true  AND t.dueDate         BETWEEN :from AND :to)
            OR (t.installment = false AND t.transactionDate BETWEEN :from AND :to))
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
          AND ((t.installment = true  AND t.dueDate         BETWEEN :from AND :to)
            OR (t.installment = false AND t.transactionDate BETWEEN :from AND :to))
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
          AND ((t.installment = true  AND t.dueDate         BETWEEN :from AND :to)
            OR (t.installment = false AND t.transactionDate BETWEEN :from AND :to))
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

    /** Suma neta de movimientos de cuenta hasta una fecha (INCOME positivo, EXPENSE negativo). */
    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0)
        FROM Transaction t
        WHERE t.user.id    = :userId
          AND t.account.id = :accountId
          AND t.deletedAt  IS NULL
          AND t.transactionDate <= :upTo
        """)
    BigDecimal netMovementsForAccount(@Param("userId")    UUID userId,
                                      @Param("accountId") UUID accountId,
                                      @Param("upTo")      LocalDate upTo);
}
