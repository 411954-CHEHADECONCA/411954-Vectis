package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentMovement;
import com.vectis.backend.domain.entity.InvestmentMovementType;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.InvestmentValuation;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.InvestmentCollectPreviewResponse;
import com.vectis.backend.dto.InvestmentCollectRequest;
import com.vectis.backend.dto.InvestmentCollectResponse;
import com.vectis.backend.dto.InvestmentMovementRequest;
import com.vectis.backend.dto.InvestmentMovementUpdateRequest;
import com.vectis.backend.dto.InvestmentRequest;
import com.vectis.backend.dto.InvestmentResponse;
import com.vectis.backend.dto.InvestmentValuationRequest;
import com.vectis.backend.exception.InvestmentDeleteBlockedException;
import com.vectis.backend.exception.InvestmentMovementNotFoundException;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentValuationNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.InvestmentMapper;
import com.vectis.backend.repository.AccountRepository;
import com.vectis.backend.repository.InvestmentMovementRepository;
import com.vectis.backend.repository.InvestmentPaymentRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.InvestmentValuationRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.util.InvestmentValuationCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentService {

    private final InvestmentRepository           investmentRepository;
    private final InvestmentMovementRepository   movementRepository;
    private final InvestmentValuationRepository  valuationRepository;
    private final InvestmentPaymentRepository    investmentPaymentRepository;
    private final AccountRepository              accountRepository;
    private final InvestmentMapper               investmentMapper;
    private final FciValuationSyncService        fciValuationSyncService;
    private final PpiValuationSyncService        ppiValuationSyncService;
    private final InvestmentPaymentSyncService   investmentPaymentSyncService;
    private final TransactionRepository          transactionRepository;
    private final MonthPeriodService             monthPeriodService;
    private final BalanceService                 balanceService;

    public List<InvestmentResponse> getInvestments(UUID userId) {
        return investmentRepository.findAllByUser_IdOrderByCreatedAtAsc(userId)
                .stream()
                .map(investmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public InvestmentResponse createInvestment(InvestmentRequest request, User user) {
        Account account = resolveAccount(request.accountId(), user, request.currency());
        assertPaymentFieldsOnlyForBondOrOn(request);
        assertPaymentAccountForForeignCurrency(request);
        Account paymentAccount = resolvePaymentAccount(request.paymentAccountId(), user, request.paymentCurrency());
        boolean includeInCashflow = resolveIncludeInCashflow(request.includeInCashflow());

        LocalDate purchaseDate = request.purchaseDate();
        // En la práctica esto sólo dispara hoy para Plazo Fijo (el resto de los tipos arranca en
        // 0 y el capital se arma con addMovement). Gatea sobre TODA la creación: si el mes está
        // cerrado se rechaza el alta completa, no sólo la transacción.
        boolean generatesTransaction = account != null
                && includeInCashflow
                && request.principal().signum() > 0;

        if (generatesTransaction
                && !monthPeriodService.isOpen(user.getId(), purchaseDate.getYear(), purchaseDate.getMonthValue())) {
            throw new VectisException(
                    "No se puede crear la inversión: el mes de la fecha de compra ya está cerrado",
                    HttpStatus.CONFLICT);
        }

        InvestmentAsset asset = InvestmentAsset.builder()
                .user(user)
                .account(account)
                .paymentAccount(paymentAccount)
                .paymentCurrency(request.paymentCurrency())
                .name(request.name())
                .type(request.type())
                .currency(request.currency())
                .principal(request.principal())
                .purchaseDate(purchaseDate)
                .maturityDate(request.maturityDate())
                // FIX 4: guard against null TNA for types that don't require it
                .tna(request.tna() != null ? request.tna() : BigDecimal.ZERO)
                .autoTrack(request.autoTrack())
                .externalId(request.externalId())
                .includeInCashflow(includeInCashflow)
                .build();

        if (generatesTransaction) {
            BigDecimal projected = balanceService.currentBalance(account, user.getId()).subtract(request.principal());
            assertSufficientFunds(account, projected);
        }

        InvestmentAsset saved = investmentRepository.save(asset);

        if (generatesTransaction) {
            Transaction tx = Transaction.builder()
                    .user(user)
                    .type(TransactionType.EXPENSE)
                    .description("Suscripción inversión: " + saved.getName())
                    .amount(saved.getPrincipal())
                    .ccy(saved.getCurrency())
                    .account(account)
                    .transactionDate(purchaseDate)
                    .dueDate(purchaseDate)
                    .installment(false)
                    .investmentAsset(saved)
                    .investmentMovement(null)
                    .investmentSourceType(InvestmentSourceType.SUSCRIPCION)
                    .build();
            transactionRepository.save(tx);
        }

        saved = backfillValuationsIfApplicable(saved);
        scheduleSyncPaymentsAfterCommit(saved, user);
        return investmentMapper.toResponse(saved);
    }

    /**
     * Dispara {@link InvestmentPaymentSyncService#syncSchedule} para un BONO/ON auto-track recién
     * creado, DESPUÉS de que la transacción de alta ya comprometió el asset — si la llamada a PPI
     * falla, el activo ya quedó creado igual. Se registra vía
     * {@link TransactionSynchronization#afterCommit()} en lugar de invocar el sync inline: la
     * llamada HTTP a PPI no debe ejecutarse mientras esta transacción retiene la conexión JDBC.
     *
     * <p>Fuera de un contexto transaccional activo (p. ej. un test que llama al método directo sin
     * proxy de Spring), se ejecuta inline como fallback — nunca se pierde el sync.
     */
    private void scheduleSyncPaymentsAfterCommit(InvestmentAsset asset, User user) {
        boolean eligible = asset.isAutoTrack()
                && (asset.getType() == InvestmentAssetType.BONO || asset.getType() == InvestmentAssetType.ON)
                && asset.getExternalId() != null && !asset.getExternalId().isBlank();
        if (!eligible) return;

        UUID assetId = asset.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        investmentPaymentSyncService.syncSchedule(assetId, user);
                    } catch (Exception e) {
                        log.warn("Sync de calendario de pagos post-alta falló para {}: {}", assetId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                investmentPaymentSyncService.syncSchedule(assetId, user);
            } catch (Exception e) {
                log.warn("Sync de calendario de pagos post-alta falló para {}: {}", assetId, e.getMessage());
            }
        }
    }

    @Transactional
    public InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        assertNotCollected(asset);

        Account account = resolveAccount(request.accountId(), user, request.currency());
        assertPaymentFieldsOnlyForBondOrOn(request);
        assertPaymentAccountForForeignCurrency(request);
        Account paymentAccount = resolvePaymentAccount(request.paymentAccountId(), user, request.paymentCurrency());

        UUID       oldAccountId = asset.getAccount() != null ? asset.getAccount().getId() : null;
        String     oldCurrency  = asset.getCurrency();
        BigDecimal oldPrincipal = asset.getPrincipal();

        asset.setName(request.name());
        asset.setType(request.type());
        asset.setCurrency(request.currency());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setMaturityDate(request.maturityDate());
        // FIX 4: guard against null TNA for types that don't require it
        asset.setTna(request.tna() != null ? request.tna() : BigDecimal.ZERO);
        asset.setAccount(account);
        asset.setPaymentAccount(paymentAccount);
        asset.setPaymentCurrency(request.paymentCurrency());
        asset.setAutoTrack(request.autoTrack());
        asset.setExternalId(request.externalId());
        asset.setIncludeInCashflow(resolveIncludeInCashflow(request.includeInCashflow()));

        // For movement-tracked types, principal is derived from movements — do not overwrite it
        boolean isMovementTracked = asset.getType() == InvestmentAssetType.FCI
                || asset.getType() == InvestmentAssetType.FCI_CUOTAPARTES
                || asset.getType() == InvestmentAssetType.LETRA
                || asset.getType() == InvestmentAssetType.BONO
                || asset.getType() == InvestmentAssetType.ON;
        if (!isMovementTracked) {
            asset.setPrincipal(request.principal());
        }

        UUID newAccountId = account != null ? account.getId() : null;
        boolean accountChanged   = !Objects.equals(oldAccountId, newAccountId);
        boolean currencyChanged  = !Objects.equals(oldCurrency, asset.getCurrency());
        boolean principalChanged = !isMovementTracked && oldPrincipal.compareTo(asset.getPrincipal()) != 0;

        if (accountChanged || currencyChanged || principalChanged) {
            syncLinkedSuscripcionTransaction(asset, account, user);
        }

        InvestmentAsset saved = investmentRepository.save(asset);
        saved = backfillValuationsIfApplicable(saved);
        return investmentMapper.toResponse(saved);
    }

    /**
     * Sincroniza la Transaction de suscripción generada en {@link #createInvestment} (cuenta, moneda,
     * monto) cuando el update cambia alguno de esos campos en la inversión ya creada. Sin esto, la
     * transacción ya contabilizada (afectó el saldo real y el cashflow de su mes) queda divergiendo
     * silenciosamente de los datos actuales de la inversión — particularmente delicado si cambia la
     * moneda en este sistema bimonetario.
     *
     * <p>Si el mes de esa transacción ya cerró, se bloquea el update con 409 en lugar de permitir el
     * drift. Si no hay transacción vinculada (p. ej. la inversión nunca generó una en su alta), no hay
     * nada que sincronizar.
     */
    private void syncLinkedSuscripcionTransaction(InvestmentAsset asset, Account newAccount, User user) {
        Optional<Transaction> linkedTx = transactionRepository
                .findAllByInvestmentAsset_IdAndDeletedAtIsNull(asset.getId())
                .stream()
                .filter(tx -> tx.getInvestmentMovement() == null
                        && tx.getInvestmentSourceType() == InvestmentSourceType.SUSCRIPCION)
                .findFirst();

        if (linkedTx.isEmpty()) {
            return;
        }

        Transaction tx = linkedTx.get();
        if (!monthPeriodService.isOpen(
                user.getId(), tx.getTransactionDate().getYear(), tx.getTransactionDate().getMonthValue())) {
            throw new VectisException(
                    "No se puede modificar: la transacción de suscripción vinculada pertenece a un mes ya cerrado",
                    HttpStatus.CONFLICT);
        }

        Account oldAccount = tx.getAccount();
        BigDecimal oldAmount = tx.getAmount();
        BigDecimal newAmount = asset.getPrincipal();

        if (newAccount != null) {
            BigDecimal balance = balanceService.currentBalance(newAccount, user.getId());
            boolean sameAccount = oldAccount != null && oldAccount.getId().equals(newAccount.getId());
            if (sameAccount) {
                balance = balance.add(oldAmount);
            }
            assertSufficientFunds(newAccount, balance.subtract(newAmount));
        }

        tx.setAccount(newAccount);
        tx.setCcy(asset.getCurrency());
        tx.setAmount(newAmount);
        transactionRepository.save(tx);
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

    /**
     * Elimina una inversión. Bloqueada por completo si alguna de sus transacciones vinculadas
     * cae en un mes ya cerrado (usar {@link #collectInvestment} en ese caso). Si todas están en
     * meses abiertos, se revierten (soft-delete) antes de borrar el activo.
     */
    @Transactional
    public void deleteInvestment(UUID id, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));

        List<Transaction> linkedTxs = transactionRepository.findAllByInvestmentAsset_IdAndDeletedAtIsNull(id);

        boolean anyClosed = linkedTxs.stream().anyMatch(tx -> !monthPeriodService.isOpen(
                user.getId(), tx.getTransactionDate().getYear(), tx.getTransactionDate().getMonthValue()));

        if (anyClosed) {
            throw new InvestmentDeleteBlockedException(id);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Transaction tx : linkedTxs) {
            tx.setDeletedAt(now);
        }
        transactionRepository.saveAll(linkedTxs);

        // NOTA: no releer `linkedTxs`/relanzar queries sobre investment_asset_id después de este
        // delete dentro de la misma transacción: el ON DELETE SET NULL de V042 nulea la FK a nivel
        // de base de datos, pero Hibernate no actualiza las entidades Transaction ya cargadas en el
        // Persistence Context — quedarían con investmentAsset apuntando a un id borrado hasta el
        // próximo refresh/reload.
        investmentRepository.delete(asset);
    }

    /**
     * Precálculo del cobro (capital/rendimiento/total) a una fecha elegida, sin efectos: no persiste
     * nada ni genera transacciones. Usado por el frontend para mostrar el desglose antes de confirmar.
     */
    public InvestmentCollectPreviewResponse previewCollect(UUID id, LocalDate date, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        assertNotAlreadyCollected(asset);

        BigDecimal residualCapitalFraction = residualCapitalFraction(asset.getId());
        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, date, residualCapitalFraction);
        boolean editableRendimiento = isEditableRendimientoType(asset.getType());

        return new InvestmentCollectPreviewResponse(
                breakdown.capital(), breakdown.rendimiento(), breakdown.total(),
                asset.getCurrency(), editableRendimiento);
    }

    /**
     * Cobra (liquida) una inversión a la fecha elegida por el usuario: acredita capital y rendimiento
     * como DOS ingresos separados a la cuenta vinculada, y marca el activo como {@code COBRADA} en vez
     * de borrarlo — sigue visible con todas sus mutaciones bloqueadas salvo {@link #deleteInvestment}.
     *
     * <p>El split capital/rendimiento sale de {@link InvestmentValuationCalculator#calculateCollectBreakdown}.
     * Para PLAZO_FIJO/FCI el rendimiento precalculado puede sobreescribirse con {@code request.rendimiento()}
     * (validado no-negativo); para la familia cuotapartes ese override se ignora siempre, porque el
     * rendimiento sale directo del valor de mercado a la fecha elegida.
     */
    @Transactional
    public InvestmentCollectResponse collectInvestment(UUID id, InvestmentCollectRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        assertNotAlreadyCollected(asset);

        LocalDate collectDate = request.collectDate();
        if (!monthPeriodService.isOpen(user.getId(), collectDate.getYear(), collectDate.getMonthValue())) {
            throw new VectisException(
                    "No se puede cobrar: el mes elegido ya está cerrado", HttpStatus.CONFLICT);
        }

        BigDecimal residualCapitalFraction = residualCapitalFraction(asset.getId());
        InvestmentValuationCalculator.CollectBreakdown breakdown =
                InvestmentValuationCalculator.calculateCollectBreakdown(asset, collectDate, residualCapitalFraction);
        boolean editableRendimiento = isEditableRendimientoType(asset.getType());

        BigDecimal rendimiento = breakdown.rendimiento();
        if (editableRendimiento && request.rendimiento() != null) {
            if (request.rendimiento().signum() < 0) {
                throw new VectisException(
                        "El rendimiento no puede ser negativo", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            rendimiento = request.rendimiento();
        }

        BigDecimal capital  = breakdown.capital();
        BigDecimal amount   = capital.add(rendimiento);
        String     currency = asset.getCurrency();

        boolean transactionCreated = asset.getAccount() != null && asset.isIncludeInCashflow();
        if (transactionCreated) {
            if (capital.signum() > 0) {
                transactionRepository.save(buildCollectTransaction(
                        asset, user, TransactionType.INCOME, capital,
                        "Cobro inversión (capital): ", collectDate, InvestmentSourceType.COLLECTION_CAPITAL));
            }
            // La familia cuotapartes puede arrojar un rendimiento negativo (pérdida real de mercado:
            // el valor a la fecha de cobro es menor al capital). No se puede postear un monto negativo
            // (transactions.amount tiene CHECK > 0), así que la pérdida se registra como un EXPENSE por
            // el valor absoluto, en vez de omitirla — de lo contrario el capital completo se acreditaría
            // ignorando la pérdida real.
            if (rendimiento.signum() > 0) {
                transactionRepository.save(buildCollectTransaction(
                        asset, user, TransactionType.INCOME, rendimiento,
                        "Cobro inversión (rendimiento): ", collectDate, InvestmentSourceType.COLLECTION_YIELD));
            } else if (rendimiento.signum() < 0) {
                transactionRepository.save(buildCollectTransaction(
                        asset, user, TransactionType.EXPENSE, rendimiento.abs(),
                        "Cobro inversión (pérdida): ", collectDate, InvestmentSourceType.COLLECTION_YIELD));
            }
        }

        asset.setStatus(InvestmentAssetStatus.COBRADA);
        asset.setCollectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        asset.setCollectDate(collectDate);
        InvestmentAsset saved = investmentRepository.save(asset);

        return new InvestmentCollectResponse(
                id, amount, currency, transactionCreated, capital, rendimiento, collectDate, saved.getStatus().name());
    }

    private Transaction buildCollectTransaction(InvestmentAsset asset, User user, TransactionType type,
                                                 BigDecimal amount, String descPrefix, LocalDate date,
                                                 InvestmentSourceType sourceType) {
        return Transaction.builder()
                .user(user)
                .type(type)
                .description(descPrefix + asset.getName())
                .amount(amount)
                .ccy(asset.getCurrency())
                .account(asset.getAccount())
                .transactionDate(date)
                .dueDate(date)
                .installment(false)
                .investmentAsset(asset)
                .investmentMovement(null)
                .investmentSourceType(sourceType)
                .build();
    }

    /** PLAZO_FIJO y FCI devengan interés en el tiempo (rendimiento editable); la familia cuotapartes no. */
    private boolean isEditableRendimientoType(InvestmentAssetType type) {
        return type == InvestmentAssetType.PLAZO_FIJO || type == InvestmentAssetType.FCI;
    }

    /** Bloquea cualquier mutación sobre un activo ya cobrado — sólo {@link #deleteInvestment} sigue permitido. */
    private void assertNotCollected(InvestmentAsset asset) {
        if (asset.getStatus() == InvestmentAssetStatus.COBRADA) {
            throw new VectisException(
                    "No se puede modificar una inversión ya cobrada", HttpStatus.CONFLICT);
        }
    }

    /** Guarda específica de los endpoints de cobro (preview/collect): evita cobrar dos veces el mismo activo. */
    private void assertNotAlreadyCollected(InvestmentAsset asset) {
        if (asset.getStatus() == InvestmentAssetStatus.COBRADA) {
            throw new VectisException("La inversión ya fue cobrada", HttpStatus.CONFLICT);
        }
    }

    // ── Movement CRUD (FCI / FCI_CUOTAPARTES) ────────────────────────────────

    @Transactional
    public InvestmentResponse addMovement(UUID investmentId, InvestmentMovementRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithMovementsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));
        assertNotCollected(asset);

        // Los movimientos pueden registrarse en cualquier fecha (incluso anterior a otros):
        // los tramos y el saldo se recalculan ordenando por fecha. Sólo validamos el rescate.
        validateRescate(asset, request);

        // Sólo SUSCRIPCION/RESCATE generan Transaction — REVALUO nunca lo hace (decisión de
        // producto: los revalúos sólo actualizan el valor de la inversión, nunca tocan la cuenta).
        boolean isTransactable = request.type() == InvestmentMovementType.SUSCRIPCION
                || request.type() == InvestmentMovementType.RESCATE;
        if (isTransactable && !monthPeriodService.isOpen(
                user.getId(), request.movementDate().getYear(), request.movementDate().getMonthValue())) {
            throw new VectisException(
                    "No se puede registrar el movimiento: el mes está cerrado", HttpStatus.CONFLICT);
        }

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

        if (isTransactable && asset.getAccount() != null && asset.isIncludeInCashflow()) {
            boolean isSuscripcion = request.type() == InvestmentMovementType.SUSCRIPCION;
            if (isSuscripcion) {
                BigDecimal projected = balanceService.currentBalance(asset.getAccount(), user.getId())
                        .subtract(request.amount());
                assertSufficientFunds(asset.getAccount(), projected);
            }
            // saveAndFlush para asegurar el id del movimiento antes de vincular la transacción.
            InvestmentMovement savedMovement = movementRepository.saveAndFlush(movement);
            Transaction tx = Transaction.builder()
                    .user(user)
                    .type(isSuscripcion ? TransactionType.EXPENSE : TransactionType.INCOME)
                    .description((isSuscripcion ? "Suscripción" : "Rescate") + " inversión: " + asset.getName())
                    .amount(request.amount())
                    .ccy(asset.getCurrency())
                    .account(asset.getAccount())
                    .transactionDate(request.movementDate())
                    .dueDate(request.movementDate())
                    .installment(false)
                    .investmentAsset(asset)
                    .investmentMovement(savedMovement)
                    .investmentSourceType(InvestmentSourceType.valueOf(request.type().name()))
                    .build();
            transactionRepository.save(tx);
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
        assertNotCollected(asset);

        if (asset.getType() != InvestmentAssetType.FCI) {
            throw new VectisException(
                    "Solo se puede editar el interés de los tramos de una Cuenta Remunerada (FCI)",
                    HttpStatus.CONFLICT);
        }

        InvestmentMovement movement = movementRepository.findByIdAndInvestmentAsset_Id(movId, investmentId)
                .orElseThrow(() -> new InvestmentMovementNotFoundException(movId));

        // Si el movimiento dejó una Transaction vinculada, bloquear la edición si su mes ya cerró.
        assertLinkedTransactionMonthOpen(movId, user.getId());

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
        assertNotCollected(asset);

        InvestmentMovement movement = movementRepository.findByIdAndInvestmentAsset_Id(movId, investmentId)
                .orElseThrow(() -> new InvestmentMovementNotFoundException(movId));

        // Si el movimiento dejó una Transaction vinculada, bloquear el borrado si su mes ya cerró;
        // si está abierto, se revierte (soft-delete) más abajo, tras remover el movimiento.
        Optional<Transaction> linkedTx = transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movId);
        linkedTx.ifPresent(tx -> assertMonthOpenForDate(tx.getTransactionDate(), user.getId(),
                "No se puede eliminar: la transacción vinculada pertenece a un mes ya cerrado"));

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

        linkedTx.ifPresent(tx -> {
            tx.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            transactionRepository.save(tx);
        });

        InvestmentAsset saved = investmentRepository.save(asset);
        return investmentMapper.toResponse(saved);
    }

    // ── Valuation CRUD (FCI_CUOTAPARTES) ─────────────────────────────────────

    @Transactional
    public InvestmentResponse addValuation(UUID investmentId, InvestmentValuationRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findWithValuationsByIdAndUser_Id(investmentId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(investmentId));
        assertNotCollected(asset);

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
        assertNotCollected(asset);

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
        assertNotCollected(asset);

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

    private void assertSufficientFunds(Account account, BigDecimal projectedBalance) {
        if (account != null && projectedBalance.signum() < 0) {
            throw new VectisException(
                    "Fondos insuficientes en la cuenta \"" + account.getName() + "\" para este movimiento",
                    HttpStatus.UNPROCESSABLE_ENTITY);
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

    private Account resolveAccount(UUID accountId, User user, String currency) {
        if (accountId == null) {
            return null;
        }
        Account account = accountRepository.findByIdAndUser_Id(accountId, user.getId())
                .orElseThrow(() -> new VectisException(
                        "La cuenta no pertenece al usuario autenticado", HttpStatus.FORBIDDEN));

        if (!account.getCcy().equals(currency)) {
            throw new VectisException(
                    "La moneda de la inversión no coincide con la moneda de la cuenta vinculada",
                    HttpStatus.CONFLICT);
        }
        return account;
    }

    /**
     * Resuelve la cuenta de cobro de renta/amortización (calcado de {@link #resolveAccount}): 403 si
     * la cuenta no pertenece al usuario, 409 si su moneda no coincide con {@code paymentCurrency}.
     * Devuelve null si no se indicó cuenta de cobro (el activo conserva {@code account} como cuenta
     * de acreditación en ese caso).
     */
    private Account resolvePaymentAccount(UUID paymentAccountId, User user, String paymentCurrency) {
        if (paymentAccountId == null) {
            return null;
        }
        Account account = accountRepository.findByIdAndUser_Id(paymentAccountId, user.getId())
                .orElseThrow(() -> new VectisException(
                        "La cuenta de cobro no pertenece al usuario autenticado", HttpStatus.FORBIDDEN));

        if (paymentCurrency != null && !account.getCcy().equals(paymentCurrency)) {
            throw new VectisException(
                    "La moneda de la cuenta de cobro no coincide con la moneda de pago indicada",
                    HttpStatus.CONFLICT);
        }
        return account;
    }

    /**
     * {@code paymentAccountId}/{@code paymentCurrency} sólo tienen sentido para BONO/ON (instrumentos
     * con calendario de renta/amortización) — 422 si vienen seteados para cualquier otro tipo.
     */
    private void assertPaymentFieldsOnlyForBondOrOn(InvestmentRequest request) {
        boolean isBondOrOn = request.type() == InvestmentAssetType.BONO || request.type() == InvestmentAssetType.ON;
        boolean hasPaymentFields = request.paymentAccountId() != null
                || (request.paymentCurrency() != null && !request.paymentCurrency().isBlank());
        if (hasPaymentFields && !isBondOrOn) {
            throw new VectisException(
                    "La cuenta/moneda de cobro sólo aplica a activos BONO u ON",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Si la moneda de cobro difiere de la de compra, la cuenta de cobro es obligatoria: sin ella el
     * cobro cae a {@code asset.account} (en la moneda de compra) y {@code confirmPayment} rechaza el
     * pago por mismatch de moneda — dejando el bono permanentemente incobrable. 422 si falta.
     */
    private void assertPaymentAccountForForeignCurrency(InvestmentRequest request) {
        boolean foreignPaymentCurrency = request.paymentCurrency() != null
                && !request.paymentCurrency().isBlank()
                && !request.paymentCurrency().equals(request.currency());
        if (foreignPaymentCurrency && request.paymentAccountId() == null) {
            throw new VectisException(
                    "Elegí una cuenta de cobro en " + request.paymentCurrency()
                            + ": los cupones se pagan en una moneda distinta a la de compra",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Fracción de capital aún no devuelta vía cupones de amortización ya cobrados (1 = nada
     * amortizado todavía) — ver {@link InvestmentValuationCalculator#calculateCollectBreakdown}.
     * Evita devolver dos veces la misma porción de capital al cobrar el activo entero después de
     * haber cobrado cupones de amortización del calendario de {@link InvestmentPaymentService}.
     */
    private BigDecimal residualCapitalFraction(UUID assetId) {
        BigDecimal amortizedPer100 = investmentPaymentRepository.sumCollectedAmortizationPer100(assetId);
        return BigDecimal.ONE.subtract(amortizedPer100.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN));
    }

    /** {@code includeInCashflow} usa Boolean (no primitivo) para poder distinguir "ausente" de "false"; default true. */
    private boolean resolveIncludeInCashflow(Boolean requested) {
        return requested == null || requested;
    }

    private void assertLinkedTransactionMonthOpen(UUID movementId, UUID userId) {
        transactionRepository.findByInvestmentMovement_IdAndDeletedAtIsNull(movementId)
                .ifPresent(tx -> assertMonthOpenForDate(tx.getTransactionDate(), userId,
                        "No se puede modificar: la transacción vinculada pertenece a un mes ya cerrado"));
    }

    private void assertMonthOpenForDate(LocalDate date, UUID userId, String messageIfClosed) {
        if (!monthPeriodService.isOpen(userId, date.getYear(), date.getMonthValue())) {
            throw new VectisException(messageIfClosed, HttpStatus.CONFLICT);
        }
    }
}
