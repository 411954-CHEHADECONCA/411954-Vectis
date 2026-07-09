package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentPaymentSource;
import com.vectis.backend.domain.entity.InvestmentPaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentPaymentRepository extends JpaRepository<InvestmentPayment, UUID> {

    List<InvestmentPayment> findAllByInvestmentAsset_IdOrderByCuttingDateAsc(UUID investmentAssetId);

    /** Pagos de un único activo con el estado dado — usado por {@code InvestmentMapper} para los call
     * sites de un solo activo (create/update/movement/valuation), donde una query extra no es N+1. */
    List<InvestmentPayment> findAllByInvestmentAsset_IdAndStatusOrderByCuttingDateAsc(
            UUID investmentAssetId, InvestmentPaymentStatus status);

    /** Batch para listados: un único IN() en vez de una query por activo — evita N+1 en
     * {@code InvestmentService#getInvestments}. */
    List<InvestmentPayment> findAllByInvestmentAsset_IdInAndStatusOrderByCuttingDateAsc(
            Collection<UUID> investmentAssetIds, InvestmentPaymentStatus status);

    Optional<InvestmentPayment> findByIdAndInvestmentAsset_Id(UUID id, UUID investmentAssetId);

    Optional<InvestmentPayment> findByInvestmentAsset_IdAndCuttingDateAndSource(
            UUID investmentAssetId, LocalDate cuttingDate, InvestmentPaymentSource source);

    /**
     * Pagos PENDIENTE ya vencidos (cutting_date <= fecha) del usuario dado — alimenta el badge.
     * EntityGraph trae el activo y sus movimientos para evitar N+1 al estimar el monto por fila
     * ({@code unitsHeldAsOf} recorre {@code asset.getMovements()}).
     */
    @EntityGraph(attributePaths = {"investmentAsset", "investmentAsset.movements"})
    @Query("""
        SELECT p FROM InvestmentPayment p
        WHERE p.investmentAsset.user.id = :userId
          AND p.status = com.vectis.backend.domain.entity.InvestmentPaymentStatus.PENDIENTE
          AND p.cuttingDate <= :onOrBefore
        ORDER BY p.cuttingDate ASC
        """)
    List<InvestmentPayment> findPendingByUser(@Param("userId") UUID userId,
                                              @Param("onOrBefore") LocalDate onOrBefore);

    /**
     * Suma de {@code amortization_per_100} de los pagos ya COBRADOS de un activo — usada para
     * descontar el capital ya amortizado del residual a devolver en {@code collectInvestment}
     * (evita doble-contabilización de capital ya percibido vía cupones de amortización).
     */
    @Query("""
        SELECT COALESCE(SUM(p.amortizationPer100), 0) FROM InvestmentPayment p
        WHERE p.investmentAsset.id = :assetId
          AND p.status = com.vectis.backend.domain.entity.InvestmentPaymentStatus.COBRADO
        """)
    BigDecimal sumCollectedAmortizationPer100(@Param("assetId") UUID assetId);

    boolean existsByInvestmentAsset_IdAndStatus(UUID investmentAssetId, InvestmentPaymentStatus status);
}
