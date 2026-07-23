package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<InvestmentAsset, UUID> {

    @EntityGraph(attributePaths = {"account"})
    List<InvestmentAsset> findAllByUser_IdOrderByCreatedAtAsc(UUID userId);

    /** Lookup + ownership check — no collections loaded (for read-only and delete). */
    Optional<InvestmentAsset> findByIdAndUser_Id(UUID id, UUID userId);

    /** Same ownership check but with movements initialized — required for addMovement / deleteMovement. */
    @EntityGraph(attributePaths = {"movements"})
    Optional<InvestmentAsset> findWithMovementsByIdAndUser_Id(UUID id, UUID userId);

    /** Same ownership check but with valuations initialized — required for addValuation / updateValuation / deleteValuation. */
    @EntityGraph(attributePaths = {"valuations"})
    Optional<InvestmentAsset> findWithValuationsByIdAndUser_Id(UUID id, UUID userId);

    /** Returns all assets of a given type that have auto-tracking enabled. Used by FciValuationSyncService. */
    List<InvestmentAsset> findAllByTypeAndAutoTrackTrue(InvestmentAssetType type);

    /** Returns all assets whose type is in the given list and have auto-tracking enabled. Used by DoctaValuationSyncService. */
    @Query("SELECT a FROM InvestmentAsset a WHERE a.type IN :types AND a.autoTrack = true")
    List<InvestmentAsset> findAllByTypesAndAutoTrackTrue(@Param("types") List<InvestmentAssetType> types);

    /**
     * Vínculos activos "cuenta remunerada": un FCI vinculado a una cuenta la vuelve remunerada.
     * Batcheado por usuario para evitar N+1 en {@code AccountService#getAccounts}.
     * Sólo activos ACTIVA: uno ya COBRADA no debe seguir marcando la cuenta como remunerada.
     */
    @Query("""
        SELECT a.account.id AS accountId, a.tna AS tna, a.createdAt AS createdAt
        FROM InvestmentAsset a
        WHERE a.user.id = :userId AND a.type = com.vectis.backend.domain.entity.InvestmentAssetType.FCI
          AND a.status = com.vectis.backend.domain.entity.InvestmentAssetStatus.ACTIVA
          AND a.account IS NOT NULL
        """)
    List<FciAccountLinkView> findFciLinksByUser(@Param("userId") UUID userId);

    /**
     * Vínculo "cuenta remunerada" para una única cuenta — usado en create/update de cuenta.
     * Sólo ACTIVA, por la misma razón que {@link #findFciLinksByUser}.
     */
    Optional<InvestmentAsset> findTopByAccount_IdAndTypeAndStatusOrderByCreatedAtDesc(
            UUID accountId, InvestmentAssetType type, InvestmentAssetStatus status);

    interface FciAccountLinkView {
        UUID getAccountId();
        java.math.BigDecimal getTna();
        java.time.OffsetDateTime getCreatedAt();
    }
}
