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
        String getCcy();
        BigDecimal getTotalAmount();
    }

    /**
     * Suma de montos agrupada por categoría <b>y moneda</b> para un tipo (INCOME/EXPENSE) y período.
     * Criterio de fecha idéntico al de {@link #search}: tarjeta por dueDate, cuenta por transactionDate.
     *
     * <p>Bimonetario: {@code ccy} está en el {@code GROUP BY}, así que una misma categoría puede
     * devolver hasta dos filas (una ARS, una USD) — el consumidor ({@link com.vectis.backend.service.CashflowService})
     * las reconstituye en un {@code MoneyByCcy} por categoría. Antes de este fix la suma colapsaba
     * ambas monedas en un único {@code BigDecimal}, tratando USD como si fuera ARS (ver bug de cobro
     * de amortización de bonos apareciendo con el monto de USD pero como pesos).
     *
     * <p>Sin {@code ORDER BY}: el orden por monto pasa al servicio, que ordena sobre el monto ya
     * normalizado a ARS con la cotización del período (una sola moneda por fila ya no garantiza que
     * "mayor SUM(t.amount)" signifique "mayor monto real").
     *
     * <p>Excluye explícitamente las transacciones vinculadas a un activo de inversión
     * ({@code investmentAsset IS NOT NULL}), aunque tengan una categoría asignada manualmente
     * (p. ej. editada desde Movimientos): esas ya se contabilizan en la sección de inversiones del
     * cashflow (SUSCRIPCION) o como ingreso sintético "Rescates de inversión" (RESCATE) — sin este
     * filtro, asignarles categoría las duplicaría en ambas secciones.
     *
     * <p>{@code category} es un {@code @ManyToOne} opcional (el formulario de Movimientos deja
     * "Sin categoría" como estado válido): un {@code LEFT JOIN} + {@code COALESCE} agrupa esas
     * transacciones sin categoría en una fila propia en vez de excluirlas — el {@code INNER JOIN}
     * implícito que generaba navegar {@code t.category.id} directamente en el SELECT las hacía
     * desaparecer del desglose (aunque sí sumaban al saldo real de la cuenta).
     *
     * <p>{@code account} también es opcional (las transacciones de tarjeta tienen {@code account = null}):
     * {@code LEFT JOIN t.account a} + {@code (t.account IS NULL OR a.includeInCashflow = true)} excluye
     * los movimientos de cuentas fuera del cashflow sin perder los consumos de tarjeta. Navegar
     * {@code t.account.includeInCashflow} directamente en el WHERE generaría un INNER JOIN implícito
     * que descartaría toda fila con {@code account = null} <b>antes</b> de evaluar el OR — por eso el
     * LEFT JOIN explícito es obligatorio acá, no cosmético.
     */
    @Query("""
        SELECT c.id                                AS categoryId,
               COALESCE(c.name, 'Sin categoría')    AS categoryName,
               COALESCE(c.icon, 'circle')           AS categoryIcon,
               COALESCE(c.color, '#9ed1c5')         AS categoryColor,
               t.ccy                                AS ccy,
               SUM(t.amount)                        AS totalAmount
        FROM Transaction t
        LEFT JOIN t.category c
        LEFT JOIN t.account a
        WHERE t.user.id = :userId AND t.deletedAt IS NULL AND t.type = :type
          AND t.transferGroupId IS NULL
          AND t.investmentAsset IS NULL
          AND (t.account IS NULL OR a.includeInCashflow = true)
          AND ((t.card IS NOT NULL AND t.dueDate         BETWEEN :from AND :to)
            OR (t.card IS NULL    AND t.transactionDate  BETWEEN :from AND :to))
        GROUP BY c.id, c.name, c.icon, c.color, t.ccy
        """)
    List<CategorySummaryProjection> groupByCategory(@Param("userId") UUID userId,
                                                    @Param("type") TransactionType type,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    Optional<Transaction> findByIdAndDeletedAtIsNull(UUID id);

    /** Transacciones vinculadas a un activo de inversión (para bloquear/revertir al eliminar). */
    List<Transaction> findAllByInvestmentAsset_IdAndDeletedAtIsNull(UUID investmentAssetId);

    /** Transacción vinculada a un movimiento puntual de inversión (para sincronizar/revertir al editar o borrar). */
    Optional<Transaction> findByInvestmentMovement_IdAndDeletedAtIsNull(UUID investmentMovementId);

    /**
     * Transacciones de inversión (suscripción/rescate/cobro) del período, con su activo ya cargado
     * (evita N+1 al agrupar por activo en {@link com.vectis.backend.service.CashflowService}).
     * Incluye COLLECTION_CAPITAL/COLLECTION_YIELD para que el cobro de una inversión impacte el
     * cashflow del mes en que se cobró (ver {@link com.vectis.backend.service.InvestmentService#collectInvestment}).
     *
     * <p>Un activo borrado deja {@code investmentAsset = NULL} vía {@code ON DELETE SET NULL}, pero
     * {@code investmentSourceType} sobrevive intacto. Estas huérfanas se incluyen acá <b>solo si nunca
     * tuvieron categoría asignada</b> ({@code category IS NULL}) — si el usuario ya le puso una
     * categoría manualmente (p. ej. transacciones viejas de antes de vincular inversión↔cuenta), esa
     * transacción ya la recupera {@link #groupByCategory} por su categoría, y contarla acá también la
     * duplicaría en el cashflow. Sin este resguardo, una huérfana categorizada aparecería dos veces:
     * una vez en "Otros ingresos/egresos" y otra en "Cobro de inversión (…)" o "Destinado a
     * inversiones" (ver {@link com.vectis.backend.service.CashflowService#buildInvestmentSection}).
     *
     * <p>Mismo filtro de {@code includeInCashflow} que {@link #groupByCategory}, con el mismo cuidado:
     * {@code LEFT JOIN t.account a} explícito — nunca navegar {@code t.account.includeInCashflow}
     * directo en el WHERE, porque generaría un INNER JOIN implícito que perdería las transacciones sin
     * cuenta (el principal de Plazo Fijo y los cobros vinculados directo al activo pueden no tener
     * {@code account} seteado).
     */
    @EntityGraph(attributePaths = {"investmentAsset"})
    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN t.account a
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.investmentSourceType IN (
              com.vectis.backend.domain.entity.InvestmentSourceType.SUSCRIPCION,
              com.vectis.backend.domain.entity.InvestmentSourceType.RESCATE,
              com.vectis.backend.domain.entity.InvestmentSourceType.COLLECTION_CAPITAL,
              com.vectis.backend.domain.entity.InvestmentSourceType.COLLECTION_YIELD,
              com.vectis.backend.domain.entity.InvestmentSourceType.COUPON_RENT,
              com.vectis.backend.domain.entity.InvestmentSourceType.AMORTIZATION)
          AND (t.investmentAsset IS NOT NULL OR t.category IS NULL)
          AND (t.account IS NULL OR a.includeInCashflow = true)
          AND t.transactionDate BETWEEN :from AND :to
        ORDER BY t.investmentAsset.id
        """)
    List<Transaction> findInvestmentTransactionsForCashflow(@Param("userId") UUID userId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

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

    /**
     * Fecha efectiva del primer movimiento real del usuario. Respeta la regla de fecha del repo:
     * transacciones de tarjeta anclan en {@code dueDate}, las de cuenta en {@code transactionDate}.
     * Excluye proyectados ({@code isProjected = false}) y borrados. Devuelve {@code null} si el
     * usuario no tiene ningún movimiento real. Usada por
     * {@link com.vectis.backend.service.CashflowService} para fijar el piso de navegación del cashflow.
     *
     * <p>A diferencia de {@link #groupByCategory} y {@link #sumByType}, <b>no</b> excluye legs de
     * transferencia ({@code transferGroupId}) ni movimientos de inversión ({@code investmentAsset}):
     * el piso representa "el mes más antiguo con actividad registrada", sin importar su naturaleza —
     * una transferencia o una suscripción también son actividad real del usuario en ese mes y deben
     * hacer navegable ese período. (Aquellas queries sí los excluyen porque contaminarían los totales
     * de ingreso/egreso; acá sólo interesa la fecha, no el monto.)
     */
    @Query("""
        SELECT MIN(CASE WHEN t.card IS NOT NULL THEN t.dueDate ELSE t.transactionDate END)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.deletedAt IS NULL
          AND t.isProjected = false
        """)
    LocalDate findEarliestMovementDate(@Param("userId") UUID userId);
}
