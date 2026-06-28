package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentMovementRequest;
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
        return investmentMapper.toResponse(saved);
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
