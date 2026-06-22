package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Proyección para el cálculo de saldo neto por cuenta en batch (cashflow). */
    interface NetMovementProjection {
        UUID getAccountId();
        BigDecimal getNetAmount();
    }

    /** Proyección para la agrupación de transacciones por categoría en el cashflow. */
    interface CategorySummaryProjection {
        UUID getCategoryId();
        String getCategoryName();
        String getCategoryIcon();
        String getCategoryColor();
        BigDecimal getTotalAmount();
    }

    /**
     * Suma de montos agrupada por categoría para un tipo (INCOME/EXPENSE) y período.
     * Criterio de fecha idéntico al de {@link #search}: tarjeta por dueDate, cuenta por transactionDate.
     */
    @Query("""
        SELECT t.category.id     AS categoryId,
               t.category.name   AS categoryName,
               t.category.icon   AS categoryIcon,
               t.category.color  AS categoryColor,
               SUM(t.amount)     AS totalAmount
        FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL AND t.type = :type
          AND t.transferGroupId IS NULL
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
        GROUP BY t.category.id, t.category.name, t.category.icon, t.category.color
        ORDER BY SUM(t.amount) DESC
        """)
    List<CategorySummaryProjection> groupByCategory(@Param("userId") UUID userId,
                                                    @Param("type") TransactionType type,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    Optional<Transaction> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Deletes all projected transactions for a user within a date range.
     * Used by MonthPeriodService before re-materializing a projected month.
     */
    void deleteByUserAndTransactionDateBetweenAndIsProjectedTrue(
            User user, LocalDate from, LocalDate to);

    List<Transaction> findAllByInstallmentGroupIdAndDeletedAtIsNull(UUID installmentGroupId);

    /** Deuda de tarjeta vigente del usuario (pendiente + futura) para la vista de Tarjetas. */
    @EntityGraph(attributePaths = {"category", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.card IS NOT NULL
          AND t.paid = FALSE
          AND t.dueDate >= :fromDate
        ORDER BY t.card.id, t.installmentGroupId, t.installmentNumber, t.dueDate
        """)
    List<Transaction> findCardDebt(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate);

    /**
     * Listado paginado con filtros opcionales. EntityGraph evita N+1 al traer las relaciones.
     * Criterio de fecha: transacciones con tarjeta por dueDate (impactan en el mes de la liquidación);
     * transacciones con cuenta por transactionDate.
     */
    @EntityGraph(attributePaths = {"category", "account", "card"})
    @Query(value = """
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY t.dueDate DESC, t.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(t) FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        """)
    Page<Transaction> search(@Param("userId") UUID userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("type") TransactionType type,
                             @Param("categoryId") UUID categoryId,
                             @Param("q") String q,
                             Pageable pageable);

    /** Suma de montos por tipo dentro del período/filtros (para el resumen). Excluye legs de transferencias. */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.type = :type
          AND t.transferGroupId IS NULL
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        """)
    BigDecimal sumByType(@Param("userId") UUID userId,
                         @Param("type") TransactionType type,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryId") UUID categoryId,
                         @Param("q") String q);

    /** Cantidad de movimientos del período/filtros (para el resumen). */
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        """)
    long countFiltered(@Param("userId") UUID userId,
                       @Param("from") LocalDate from,
                       @Param("to") LocalDate to,
                       @Param("type") TransactionType type,
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

    /**
     * Suma neta de movimientos para múltiples cuentas hasta una fecha — una sola query (evita N+1 en cashflow).
     * Las transacciones de tarjeta (card != null) tienen account=null y quedan excluidas naturalmente;
     * todas las de cuenta usan transactionDate como ancla de período.
     * Devuelve sólo las cuentas que tienen al menos un movimiento; las que no aparezcan tienen neto = 0.
     */
    @Query("""
        SELECT t.account.id AS accountId,
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) AS netAmount
        FROM Transaction t
        WHERE t.user.id     = :userId
          AND t.account.id IN :accountIds
          AND t.deletedAt  IS NULL
          AND t.transactionDate <= :upTo
        GROUP BY t.account.id
        """)
    List<NetMovementProjection> netMovementsForAccounts(@Param("userId")     UUID userId,
                                                        @Param("accountIds") List<UUID> accountIds,
                                                        @Param("upTo")       LocalDate upTo);

    /**
     * Cuotas de tarjeta pendientes de pago para un período dado.
     * Usada por {@link com.vectis.backend.service.CardPaymentService} para marcarlas como pagadas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id   = :userId
          AND t.card.id   = :cardId
          AND t.deletedAt IS NULL
          AND t.paid      = FALSE
          AND t.dueDate   BETWEEN :from AND :to
        ORDER BY t.dueDate ASC
        """)
    List<Transaction> findUnpaidCardTransactionsForPeriod(
            @Param("userId") UUID userId,
            @Param("cardId") UUID cardId,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);

    /**
     * Todas las cuotas de tarjeta del usuario desde una fecha (incluyendo pagadas).
     * Usada por {@link com.vectis.backend.service.CardProjectionService} para la matriz,
     * que debe mostrar cuotas pagadas con estilo diferenciado.
     */
    @EntityGraph(attributePaths = {"category", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.card IS NOT NULL
          AND t.dueDate >= :fromDate
        ORDER BY t.card.id, t.installmentGroupId, t.installmentNumber, t.dueDate
        """)
    List<Transaction> findCardTransactionsFromDate(
            @Param("userId")   UUID userId,
            @Param("fromDate") LocalDate fromDate);

    /**
     * Transacciones que son el pago principal de una liquidación (otras transacciones
     * apuntan a ellas mediante card_payment_id).
     */
    @EntityGraph(attributePaths = {"account", "category"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.id IN (SELECT t2.cardPaymentId FROM Transaction t2
                       WHERE t2.cardPaymentId IS NOT NULL AND t2.user.id = :userId)
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<Transaction> findCardPaymentTransactions(@Param("userId") UUID userId);

    /**
     * Cargos extra vinculados a pagos de una tarjeta (card == null, cardPaymentId != null).
     * Usados por {@link com.vectis.backend.service.CardProjectionService} para incluirlos
     * en la matriz de financiamiento como fila adicional fuera del límite de crédito.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.cardPaymentId IN (
            SELECT DISTINCT cuota.cardPaymentId FROM Transaction cuota
            WHERE cuota.card.id = :cardId
              AND cuota.cardPaymentId IS NOT NULL
              AND cuota.deletedAt IS NULL
        )
        AND t.card IS NULL
        AND t.deletedAt IS NULL
        AND t.transactionDate >= :fromDate
        ORDER BY t.transactionDate ASC
        """)
    List<Transaction> findExtraChargesForCard(
            @Param("cardId")   UUID cardId,
            @Param("fromDate") LocalDate fromDate);

    /**
     * Cuotas y cargos adicionales vinculados a un pago de tarjeta dado.
     * El servicio separa cuotas (card != null) de extras (card == null) mediante filter().
     */
    @EntityGraph(attributePaths = {"category", "card"})
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.cardPaymentId = :paymentId
        ORDER BY t.amount DESC
        """)
    List<Transaction> findByCardPaymentId(@Param("paymentId") UUID paymentId);

    /**
     * Soft-delete atómico de las dos legs de una transferencia.
     * Devuelve la cantidad de registros actualizados (debe ser 2 en condiciones normales).
     */
    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.deletedAt = :now
        WHERE t.transferGroupId = :groupId
          AND t.user.id = :userId
          AND t.deletedAt IS NULL
        """)
    int softDeleteByTransferGroupId(@Param("groupId") UUID groupId,
                                     @Param("userId")  UUID userId,
                                     @Param("now")     OffsetDateTime now);
}
