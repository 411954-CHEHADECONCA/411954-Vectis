package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.InvestmentMovementNotFoundException;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentValuationNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.InvestmentMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.InvestmentMovementRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentService {

    private final InvestmentRepository           investmentRepository;
    private final InvestmentMovementRepository   movementRepository;
    private final InvestmentValuationRepository  valuationRepository;
    private final AccountRepository              accountRepository;
    private final InvestmentMapper               investmentMapper;
    private final FciValuationSyncService        fciValuationSyncService;
    private final PpiValuationSyncService        ppiValuationSyncService;

    public List<InvestmentResponse> getInvestments(UUID userId) {
        return investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)
                .stream()
                .map(investmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public InvestmentResponse createInvestment(InvestmentRequest request, User user) {
        Account account = resolveAccount(request.accountId(), user);

        InvestmentAsset asset = InvestmentAsset.builder()
                .user(user)
                .account(account)
                .name(request.name())
                .type(request.type())
                .currency(request.currency())
                .principal(request.principal())
                .purchaseDate(request.purchaseDate())
                .maturityDate(request.maturityDate())
                // FIX 4: guard against null TNA for types that don't require it
                .tna(request.tna() != null ? request.tna() : BigDecimal.ZERO)
                .autoTrack(request.autoTrack())
                .externalId(request.externalId())
                .build();

        InvestmentAsset saved = investmentRepository.save(asset);
        saved = backfillValuationsIfApplicable(saved);
        return investmentMapper.toResponse(saved);
    }

    @Transactional
    public InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));

        Account account = resolveAccount(request.accountId(), user);

        asset.setName(request.name());
        asset.setType(request.type());
        asset.setCurrency(request.currency());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setMaturityDate(request.maturityDate());
        // FIX 4: guard against null TNA for types that don't require it
        asset.setTna(request.tna() != null ? request.tna() : BigDecimal.ZERO);
        asset.setAccount(account);
        asset.setAutoTrack(request.autoTrack());
        asset.setExternalId(request.externalId());

        // For movement-tracked types, principal is derived from movements — do not overwrite it
        if (asset.getType() != InvestmentAssetType.FCI
                && asset.getType() != InvestmentAssetType.FCI_CUOTAPARTES
                && asset.getType() != InvestmentAssetType.LETRA
                && asset.getType() != InvestmentAssetType.BONO
                && asset.getType() != InvestmentAssetType.ON) {
            asset.setPrincipal(request.principal());
        }

        InvestmentAsset saved = investmentRepository.save(asset);
        saved = backfillValuationsIfApplicable(saved);
        return investmentMapper.toResponse(saved);
    }

    /**
     * Para activos con seguimiento automático, rellena las valuaciones históricas faltantes (desde la
     * fecha de compra hasta hoy) usando la serie de precios de la fuente correspondiente:
     * FCI_CUOTAPARTES → VCP de argentinadatos; LETRA/BONO/ON → precios de PPI.
     * Resiliente: si la API falla, el activo se devuelve igual sin las valuaciones extra.
     */
    private InvestmentAsset backfillValuationsIfApplicable(InvestmentAsset asset) {
        if (!asset.isAutoTrack()
                || asset.getExternalId() == null || asset.getExternalId().isBlank()) {
            return asset;
        }
        int filled = switch (asset.getType()) {
            case FCI_CUOTAPARTES     -> fciValuationSyncService.backfillValuations(asset);
            case LETRA, BONO, ON     -> ppiValuationSyncService.backfillValuations(asset);
            default                  -> 0;
        };
        return filled > 0 ? investmentRepository.save(asset) : asset;
    }

    @Transactional
    public void deleteInvestment(UUID id, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));

        investmentRepository.delete(asset);
    }

    // ── Movement CRUD (FCI / FCI_CUOTAPARTES) ────────────────────────────────

    @Transactional
    public InvestmentResponse addMovement(UUID investmentId, InvestmentMovementRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithMovementsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        // Los movimientos pueden registrarse en cualquier fecha (incluso anterior a otros):
        // los tramos y el saldo se recalculan ordenando por fecha. Sólo validamos el rescate.
        validateRescate(asset, request);

        InvestmentMovement movement = InvestmentMovement.builder()
                .investmentAsset(asset)
                .movementDate(request.movementDate())
                .type(request.type())
                .amount(request.amount())
                .units(request.units())
                .build();

        asset.getMovements().add(movement);
        recalculatePrincipal(asset);

        // Familia cuotapartes: registrar el valor histórico de la operación como valuación a su fecha,
        // para que toda operación (susc/rescate) deje su marca de precio, igual que el revalúo.
        // Se excluye FCI (Cuenta Remunerada, rinde por TNA) y PLAZO_FIJO (sin valuaciones).
        // Nota: el activo se cargó con @EntityGraph de `movements`; acceder a `valuations` aquí dispara
        // un único SELECT lazy adicional (mitigado por @BatchSize). Es deliberado: no se puede hacer
        // JOIN FETCH de ambas colecciones (List) a la vez sin MultipleBagFetchException.
        if (isCuotaparteFamily(asset.getType())) {
            BigDecimal price = resolveMovementPrice(request);
            if (price != null) {
                upsertValuationForMovement(asset, request.movementDate(), price);
            }
        }

        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    /**
     * Edita un movimiento de una Cuenta Remunerada (FCI): ajusta el monto (tramo REVALUO,
     * capitaliza) y/o fija el override de interés del tramo (SUSCRIPCION/RESCATE, no capitaliza).
     * Restringido a FCI para no afectar el cálculo de los demás tipos de inversión.
     */
    @Transactional
    public InvestmentResponse updateMovement(UUID investmentId, UUID movId,
                                             InvestmentMovementUpdateRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithMovementsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        if (asset.getType() != InvestmentAssetType.FCI) {
            throw new VectisException(
                    "Solo se puede editar el interés de los tramos de una Cuenta Remunerada (FCI)",
                    HttpStatus.CONFLICT);
        }

        InvestmentMovement movement = movementRepository.findByIdAndInvestmentAsset_Id(movId, investmentId)
                .orElseThrow(() -> new InvestmentMovementNotFoundException(movId));

        // amount: solo se actualiza si viene presente (tramo REVALUO).
        if (request.amount() != null) {
            movement.setAmount(request.amount());
        }
        // interestOverride: se aplica siempre el valor recibido (null = restaurar cálculo por TNA).
        movement.setInterestOverride(request.interestOverride());

        recalculatePrincipal(asset);

        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    @Transactional
    public InvestmentResponse deleteMovement(UUID investmentId, UUID movId, User user) {
        InvestmentAsset asset = investmentRepository.findWithMovementsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        InvestmentMovement movement = movementRepository.findByIdAndInvestmentAsset_Id(movId, investmentId)
                .orElseThrow(() -> new InvestmentMovementNotFoundException(movId));

        asset.getMovements().remove(movement);
        recalculatePrincipal(asset);

        // Ciclo de vida simétrico: si el movimiento había dejado su valuación de operación
        // (familia cuotapartes), quitarla al borrarlo para no dejar una marca de precio huérfana
        // que `calcTramosCP` tomaría como evento. Sólo se remueve la MANUAL derivada de la operación
        // y sólo si ningún otro movimiento queda en esa fecha; las de mercado (PPI/ARGENTINADATOS)
        // y las de cierre de mes (SYSTEM) se preservan.
        if (isCuotaparteFamily(asset.getType())) {
            removeOperationValuationIfOrphan(asset, movement.getMovementDate());
        }

        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    // ── Valuation CRUD (FCI_CUOTAPARTES) ─────────────────────────────────────

    @Transactional
    public InvestmentResponse addValuation(UUID investmentId, InvestmentValuationRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithValuationsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        // FIX 2: reject duplicate valuation date for this asset
        validateValuationDateUniqueness(asset, request.valuationDate(), null);

        InvestmentValuation valuation = InvestmentValuation.builder()
                .investmentAsset(asset)
                .valuationDate(request.valuationDate())
                .pricePerUnit(request.pricePerUnit())
                .build();

        asset.getValuations().add(valuation);
        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    @Transactional
    public InvestmentResponse updateValuation(UUID investmentId, UUID valId,
                                              InvestmentValuationRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithValuationsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        InvestmentValuation valuation = valuationRepository.findByIdAndInvestmentAsset_Id(valId, investmentId)
                .orElseThrow(() -> new InvestmentValuationNotFoundException(valId));

        // FIX 2: reject duplicate valuation date, excluding the valuation being edited
        validateValuationDateUniqueness(asset, request.valuationDate(), valId);

        valuation.setValuationDate(request.valuationDate());
        valuation.setPricePerUnit(request.pricePerUnit());
        valuation.setSource("MANUAL");

        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    @Transactional
    public InvestmentResponse deleteValuation(UUID investmentId, UUID valId, User user) {
        InvestmentAsset asset = investmentRepository.findWithValuationsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));

        InvestmentValuation valuation = valuationRepository.findByIdAndInvestmentAsset_Id(valId, investmentId)
                .orElseThrow(() -> new InvestmentValuationNotFoundException(valId));

        asset.getValuations().remove(valuation);
        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Tipos cuyo rendimiento deriva de valuaciones/precio (no de TNA): usan marca de precio por operación. */
    private boolean isCuotaparteFamily(InvestmentAssetType type) {
        return type == InvestmentAssetType.FCI_CUOTAPARTES
                || type == InvestmentAssetType.LETRA
                || type == InvestmentAssetType.BONO
                || type == InvestmentAssetType.ON;
    }

    /**
     * Precio de la operación: el {@code pricePerUnit} explícito si viene; si no, el implícito
     * {@code amount / units} (sólo si hay unidades positivas). Devuelve null si no se puede determinar.
     */
    private BigDecimal resolveMovementPrice(InvestmentMovementRequest request) {
        if (request.pricePerUnit() != null && request.pricePerUnit().signum() > 0) {
            // Escala 4 siempre (columna NUMERIC(19,4)) con HALF_EVEN, igual que el fallback y el sync PPI.
            return request.pricePerUnit().setScale(4, RoundingMode.HALF_EVEN);
        }
        if (request.units() != null && request.units().signum() > 0) {
            return request.amount().divide(request.units(), 4, RoundingMode.HALF_EVEN);
        }
        return null;
    }

    /**
     * Upsert de la valuación de una operación a una fecha: si ya existe una para esa fecha, actualiza
     * su precio; si no, agrega una nueva a la colección del activo (se persiste por cascade). No lanza
     * por duplicado (a diferencia de {@link #addValuation}), porque un movimiento y su valuación
     * comparten fecha por diseño.
     *
     * <p>Precedencia de fuente: sólo se sobreescribe una valuación {@code MANUAL}. Los precios de
     * mercado oficiales ({@code PPI}/{@code ARGENTINADATOS}) y los cierres automáticos ({@code SYSTEM})
     * NO se pisan con el precio implícito de una operación — se conservan como más confiables.
     */
    private void upsertValuationForMovement(InvestmentAsset asset, LocalDate date, BigDecimal price) {
        asset.getValuations().stream()
                .filter(v -> v.getValuationDate().equals(date))
                .findFirst()
                .ifPresentOrElse(
                        existing -> {
                            if ("MANUAL".equals(existing.getSource())) {
                                existing.setPricePerUnit(price);
                            }
                        },
                        () -> asset.getValuations().add(InvestmentValuation.builder()
                                .investmentAsset(asset)
                                .valuationDate(date)
                                .pricePerUnit(price)
                                .source("MANUAL")
                                .build()));
    }

    /**
     * Al borrar un movimiento de la familia cuotapartes, elimina la valuación MANUAL a su fecha
     * (la marca de precio que había dejado la operación) siempre que ya no quede ningún otro
     * movimiento en esa misma fecha. Preserva las valuaciones de mercado (PPI/ARGENTINADATOS) y de
     * cierre de mes (SYSTEM). Debe llamarse DESPUÉS de remover el movimiento de la colección.
     */
    private void removeOperationValuationIfOrphan(InvestmentAsset asset, LocalDate date) {
        boolean otherMovementSameDate = asset.getMovements().stream()
                .anyMatch(m -> m.getMovementDate().equals(date));
        if (otherMovementSameDate) return;
        asset.getValuations().removeIf(v -> v.getValuationDate().equals(date)
                && "MANUAL".equals(v.getSource()));
    }

    private void recalculatePrincipal(InvestmentAsset asset) {
        BigDecimal balance = asset.getMovements().stream()
                .map(m -> switch (m.getType()) {
                    case SUSCRIPCION -> m.getAmount();
                    case RESCATE     -> m.getAmount().negate();
                    // REVALUO compounds into FCI NAV; FCI_CUOTAPARTES capital stays at cost basis
                    case REVALUO     -> asset.getType() == InvestmentAssetType.FCI
                                        ? m.getAmount()
                                        : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        asset.setPrincipal(balance);
    }

    /**
     * FIX 2: Ensures no two valuations share the same date for the same asset.
     * When editing an existing valuation, pass its ID as excludeId to avoid self-collision.
     */
    private void validateValuationDateUniqueness(InvestmentAsset asset, LocalDate date, UUID excludeId) {
        boolean exists = asset.getValuations().stream()
                .filter(v -> excludeId == null || !v.getId().equals(excludeId))
                .anyMatch(v -> v.getValuationDate().equals(date));
        if (exists) {
            throw new VectisException(
                    "Ya existe una valuación registrada para la fecha " + date,
                    HttpStatus.CONFLICT);
        }
    }

    private void validateRescate(InvestmentAsset asset, InvestmentMovementRequest request) {
        if (request.type() != InvestmentMovementType.RESCATE) return;

        if (request.amount().compareTo(asset.getPrincipal()) > 0) {
            throw new VectisException(
                    "El rescate supera el saldo disponible del fondo",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // FIX 3: extend nominal unit validation to LETRA, BONO, and ON (not only FCI_CUOTAPARTES)
        boolean isNominalTracked = asset.getType() == InvestmentAssetType.FCI_CUOTAPARTES
                || asset.getType() == InvestmentAssetType.LETRA
                || asset.getType() == InvestmentAssetType.BONO
                || asset.getType() == InvestmentAssetType.ON;
        if (isNominalTracked && request.units() != null) {
            BigDecimal held = calcCuotapartesHeld(asset);
            if (request.units().compareTo(held) > 0) {
                throw new VectisException(
                        "El rescate supera los nominales disponibles",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    /** Calculates the net units/nominales held: sum of SUSCRIPCION units minus sum of RESCATE units. */
    private BigDecimal calcCuotapartesHeld(InvestmentAsset asset) {
        return asset.getMovements().stream()
                .filter(m -> m.getUnits() != null)
                .map(m -> m.getType() == InvestmentMovementType.SUSCRIPCION
                        ? m.getUnits()
                        : m.getUnits().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Account resolveAccount(UUID accountId, User user) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUser_Id(accountId, user.getId())
                .orElseThrow(() -> new VectisException(
                        "La cuenta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN));
    }
}
