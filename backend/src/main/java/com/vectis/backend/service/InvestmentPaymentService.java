package com.vectis.backend.service;

import com.vectis.backend.config.PpiMarketDataClient.PpiBondEstimate;
import com.vectis.backend.config.PpiMarketDataClient.PpiBondFlow;
import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.domain.entity.InvestmentAsset;
import com.vectis.backend.domain.entity.InvestmentAssetStatus;
import com.vectis.backend.domain.entity.InvestmentAssetType;
import com.vectis.backend.domain.entity.InvestmentPayment;
import com.vectis.backend.domain.entity.InvestmentPaymentSource;
import com.vectis.backend.domain.entity.InvestmentPaymentStatus;
import com.vectis.backend.domain.entity.InvestmentSourceType;
import com.vectis.backend.domain.entity.Transaction;
import com.vectis.backend.domain.entity.TransactionType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.ConfirmPaymentRequest;
import com.vectis.backend.dto.ConfirmPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentRequest;
import com.vectis.backend.dto.InvestmentPaymentResponse;
import com.vectis.backend.dto.InvestmentPaymentUpdateRequest;
import com.vectis.backend.dto.PendingPaymentResponse;
import com.vectis.backend.exception.InvestmentNotFoundException;
import com.vectis.backend.exception.InvestmentPaymentNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.repository.InvestmentPaymentRepository;
import com.vectis.backend.repository.InvestmentRepository;
import com.vectis.backend.repository.TransactionRepository;
import com.vectis.backend.util.InvestmentValuationCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestiona el calendario de pagos (renta + amortización) de BONO/ON: persistencia del calendario
 * ({@link #mergeSchedule}), lectura ({@link #getPayments}), edición manual, confirmación de cobro
 * (genera las Transactions) y su reverso.
 *
 * <p>La sincronización HTTP contra PPI vive en {@link InvestmentPaymentSyncService} (bean separado,
 * sin transacción de clase, para no retener conexión JDBC durante la llamada externa), que delega
 * en este servicio la persistencia y la relectura vía proxy. Separación deliberada también de
 * {@link InvestmentService}: este servicio no depende de él (evita ciclo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentPaymentService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode RM = RoundingMode.HALF_EVEN;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 4;
    private static final int FACTOR_SCALE = 6;

    private final InvestmentRepository        investmentRepository;
    private final InvestmentPaymentRepository paymentRepository;
    private final TransactionRepository       transactionRepository;
    private final MonthPeriodService          monthPeriodService;

    // ─── Persistencia del calendario (invocada por InvestmentPaymentSyncService) ──

    /**
     * Persiste (merge idempotente) el calendario recibido de PPI. Por cada flow, upsert por
     * {@code (asset, cuttingDate, source=PPI)}:
     * <ul>
     *   <li>No existe: inserta una fila nueva PENDIENTE.</li>
     *   <li>Existe, PENDIENTE y no editada por el usuario: actualiza los factores si cambiaron
     *       (bonos CER, cuyo monto proyectado varía con la inflación).</li>
     *   <li>Existe pero {@code userEdited=true} o {@code status != PENDIENTE}: NO se toca.</li>
     * </ul>
     * Nunca borra filas ausentes en la respuesta nueva de PPI. Si el activo no tenía
     * {@code paymentCurrency} seteada, la fija con la moneda normalizada de PPI.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeSchedule(UUID assetId, PpiBondEstimate estimate, String currency) {
        InvestmentAsset asset = investmentRepository.findById(assetId).orElse(null);
        if (asset == null) return;

        if (asset.getPaymentCurrency() == null) {
            asset.setPaymentCurrency(currency);
            investmentRepository.save(asset);
        }

        for (PpiBondFlow flow : estimate.flows()) {
            if (flow.cuttingDate() == null) continue;

            BigDecimal rentPer100 = flow.rent().setScale(FACTOR_SCALE, RM);
            BigDecimal amortPer100 = flow.amortization().setScale(FACTOR_SCALE, RM);
            BigDecimal residualAfter = flow.residualValue()
                    .multiply(HUNDRED, MC)
                    .subtract(flow.amortization(), MC)
                    .setScale(FACTOR_SCALE, RM);

            Optional<InvestmentPayment> existing = paymentRepository
                    .findByInvestmentAsset_IdAndCuttingDateAndSource(assetId, flow.cuttingDate(), InvestmentPaymentSource.PPI);

            if (existing.isEmpty()) {
                paymentRepository.save(InvestmentPayment.builder()
                        .investmentAsset(asset)
                        .cuttingDate(flow.cuttingDate())
                        .rentPer100(rentPer100)
                        .amortizationPer100(amortPer100)
                        .residualAfterPer100(residualAfter)
                        .currency(currency)
                        .status(InvestmentPaymentStatus.PENDIENTE)
                        .source(InvestmentPaymentSource.PPI)
                        .userEdited(false)
                        .build());
                continue;
            }

            InvestmentPayment payment = existing.get();
            if (payment.getStatus() != InvestmentPaymentStatus.PENDIENTE || payment.isUserEdited()) {
                continue; // nunca se pisa lo ya cobrado/omitido/editado por el usuario
            }
            boolean changed = payment.getRentPer100().compareTo(rentPer100) != 0
                    || payment.getAmortizationPer100().compareTo(amortPer100) != 0
                    || (payment.getResidualAfterPer100() == null || payment.getResidualAfterPer100().compareTo(residualAfter) != 0)
                    || !currency.equals(payment.getCurrency());
            if (changed) {
                payment.setRentPer100(rentPer100);
                payment.setAmortizationPer100(amortPer100);
                payment.setResidualAfterPer100(residualAfter);
                payment.setCurrency(currency);
                paymentRepository.save(payment);
            }
        }
    }

    // ─── Lectura ─────────────────────────────────────────────────────────────

    public List<InvestmentPaymentResponse> getPayments(UUID assetId, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        return paymentRepository.findAllByInvestmentAsset_IdOrderByCuttingDateAsc(assetId).stream()
                .map(p -> toResponse(p, asset))
                .toList();
    }

    /** Pagos PENDIENTE ya vencidos (cutting_date <= hoy) del usuario autenticado — alimenta el badge. */
    public List<PendingPaymentResponse> getPendingPayments(User user) {
        return paymentRepository.findPendingByUser(user.getId(), LocalDate.now(ZoneOffset.UTC)).stream()
                .map(this::toPendingResponse)
                .toList();
    }

    // ─── Mutaciones ──────────────────────────────────────────────────────────

    @Transactional
    public InvestmentPaymentResponse createManualPayment(UUID assetId, InvestmentPaymentRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        assertBondOrOn(asset);

        boolean duplicate = paymentRepository
                .findByInvestmentAsset_IdAndCuttingDateAndSource(assetId, request.cuttingDate(), InvestmentPaymentSource.MANUAL)
                .isPresent();
        if (duplicate) {
            throw new VectisException(
                    "Ya existe un pago manual registrado para la fecha " + request.cuttingDate(), HttpStatus.CONFLICT);
        }

        InvestmentPayment payment = InvestmentPayment.builder()
                .investmentAsset(asset)
                .cuttingDate(request.cuttingDate())
                .rentPer100(BigDecimal.ZERO)
                .amortizationPer100(BigDecimal.ZERO)
                .rentAmountOverride(request.rentAmountOrZero())
                .amortizationAmountOverride(request.amortizationAmountOrZero())
                .currency(request.currency())
                .status(InvestmentPaymentStatus.PENDIENTE)
                .source(InvestmentPaymentSource.MANUAL)
                .userEdited(true)
                .build();

        InvestmentPayment saved = paymentRepository.save(payment);
        return toResponse(saved, asset);
    }

    /** Edita un pago PENDIENTE — sólo se actualizan los campos presentes en el request (resto intacto). */
    @Transactional
    public InvestmentPaymentResponse updatePayment(UUID assetId, UUID paymentId, InvestmentPaymentUpdateRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        InvestmentPayment payment = paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)
                .orElseThrow(() -> new InvestmentPaymentNotFoundException(paymentId));
        assertPending(payment);

        if (request.cuttingDate() != null) {
            payment.setCuttingDate(request.cuttingDate());
        }
        if (request.rentAmount() != null) {
            payment.setRentAmountOverride(request.rentAmount());
        }
        if (request.amortizationAmount() != null) {
            payment.setAmortizationAmountOverride(request.amortizationAmount());
        }
        payment.setUserEdited(true);

        InvestmentPayment saved = paymentRepository.save(payment);
        return toResponse(saved, asset);
    }

    @Transactional
    public InvestmentPaymentResponse omitPayment(UUID assetId, UUID paymentId, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        InvestmentPayment payment = paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)
                .orElseThrow(() -> new InvestmentPaymentNotFoundException(paymentId));
        assertPending(payment);

        payment.setStatus(InvestmentPaymentStatus.OMITIDO);
        InvestmentPayment saved = paymentRepository.save(payment);
        return toResponse(saved, asset);
    }

    @Transactional
    public void deletePayment(UUID assetId, UUID paymentId, User user) {
        investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        InvestmentPayment payment = paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)
                .orElseThrow(() -> new InvestmentPaymentNotFoundException(paymentId));

        if (payment.getSource() != InvestmentPaymentSource.MANUAL || payment.getStatus() != InvestmentPaymentStatus.PENDIENTE) {
            throw new VectisException(
                    "Sólo se puede eliminar un pago manual pendiente", HttpStatus.CONFLICT);
        }
        paymentRepository.delete(payment);
    }

    /**
     * Confirma el cobro de un pago del calendario: genera hasta dos {@link Transaction} (renta y/o
     * amortización) en la cuenta de cobro ({@code asset.paymentAccount} si existe, si no
     * {@code asset.account}), y marca el pago {@code COBRADO}. Si el pago confirmado es el final
     * (residual 0 tras este pago) — o si PPI no informa el residual ({@code null}), como fallback —
     * y no quedan más pagos {@code PENDIENTE} para el activo, el activo pasa automáticamente a
     * {@code COBRADA} — misma semántica que {@code collectInvestment}: no
     * toca {@code principal} ni crea {@code InvestmentMovement}, porque el precio de mercado ya
     * cotiza sobre el residual (units × price sigue siendo válido para la valuación corriente).
     */
    @Transactional
    public ConfirmPaymentResponse confirmPayment(UUID assetId, UUID paymentId, ConfirmPaymentRequest request, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        if (asset.getStatus() != InvestmentAssetStatus.ACTIVA) {
            throw new VectisException("El activo no está activo — no se puede confirmar un cobro", HttpStatus.CONFLICT);
        }

        InvestmentPayment payment = paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)
                .orElseThrow(() -> new InvestmentPaymentNotFoundException(paymentId));
        assertPending(payment);

        LocalDate collectedDate = request.collectedDate();
        if (collectedDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new VectisException("No se puede confirmar un cobro con fecha futura",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!monthPeriodService.isOpen(user.getId(), collectedDate.getYear(), collectedDate.getMonthValue())) {
            throw new VectisException("No se puede confirmar: el mes elegido ya está cerrado", HttpStatus.CONFLICT);
        }

        Account creditAccount = asset.getPaymentAccount() != null ? asset.getPaymentAccount() : asset.getAccount();
        if (creditAccount != null && !creditAccount.getCcy().equals(payment.getCurrency())) {
            throw new VectisException(
                    "La moneda de la cuenta de cobro no coincide con la moneda del pago", HttpStatus.CONFLICT);
        }

        int transactionsCreated = 0;
        boolean canPost = creditAccount != null && asset.isIncludeInCashflow();
        if (canPost) {
            if (request.rentAmount().signum() > 0) {
                Transaction tx = buildPaymentTransaction(asset, user, creditAccount, request.rentAmount(),
                        "Cupón de renta: " + asset.getName(), collectedDate, InvestmentSourceType.COUPON_RENT, payment.getCurrency());
                payment.setRentTransaction(transactionRepository.save(tx));
                transactionsCreated++;
            }
            if (request.amortizationAmount().signum() > 0) {
                Transaction tx = buildPaymentTransaction(asset, user, creditAccount, request.amortizationAmount(),
                        "Amortización: " + asset.getName(), collectedDate, InvestmentSourceType.AMORTIZATION, payment.getCurrency());
                payment.setAmortizationTransaction(transactionRepository.save(tx));
                transactionsCreated++;
            }
        }

        payment.setStatus(InvestmentPaymentStatus.COBRADO);
        payment.setCollectedDate(collectedDate);
        InvestmentPayment savedPayment = paymentRepository.save(payment);

        BigDecimal residual = savedPayment.getResidualAfterPer100();
        // Cierre por residual 0 (pago final explícito) O por fallback cuando PPI no informa el
        // residual (null): en ambos casos se cierra solo si no quedan pagos PENDIENTE. Un residual
        // conocido > 0 significa que aún hay capital/flujos por delante: NO se cierra (ni se consulta).
        boolean residualZeroOrUnknown = residual == null || residual.signum() == 0;
        boolean assetCollected = false;
        if (residualZeroOrUnknown) {
            boolean anyStillPending = paymentRepository
                    .existsByInvestmentAsset_IdAndStatus(assetId, InvestmentPaymentStatus.PENDIENTE);
            if (!anyStillPending) {
                asset.setStatus(InvestmentAssetStatus.COBRADA);
                asset.setCollectedAt(OffsetDateTime.now(ZoneOffset.UTC));
                asset.setCollectDate(collectedDate);
                investmentRepository.save(asset);
                assetCollected = true;
            }
        }

        return ConfirmPaymentResponse.builder()
                .payment(toResponse(savedPayment, asset))
                .transactionsCreated(transactionsCreated)
                .assetCollected(assetCollected)
                .build();
    }

    /**
     * Revierte un pago ya {@code COBRADO} a {@code PENDIENTE}, soft-deleteando ({@code deletedAt})
     * sus transacciones vinculadas — mismo mecanismo de reverso usado en el resto del módulo de
     * inversiones ({@code InvestmentService#deleteMovement}). Bloqueado si el mes de la fecha de
     * cobro ya cerró, o si el activo ya está {@code COBRADA} (evita dejar un pago PENDIENTE
     * "huérfano" en un activo ya liquidado).
     */
    @Transactional
    public InvestmentPaymentResponse revertPayment(UUID assetId, UUID paymentId, User user) {
        InvestmentAsset asset = investmentRepository.findByIdAndUser_Id(assetId, user.getId())
                .orElseThrow(() -> new InvestmentNotFoundException(assetId));
        InvestmentPayment payment = paymentRepository.findByIdAndInvestmentAsset_Id(paymentId, assetId)
                .orElseThrow(() -> new InvestmentPaymentNotFoundException(paymentId));

        if (payment.getStatus() != InvestmentPaymentStatus.COBRADO) {
            throw new VectisException("Sólo se puede revertir un pago cobrado", HttpStatus.CONFLICT);
        }
        if (asset.getStatus() == InvestmentAssetStatus.COBRADA) {
            throw new VectisException(
                    "No se puede revertir: el activo ya fue cobrado en su totalidad", HttpStatus.CONFLICT);
        }
        if (payment.getCollectedDate() != null
                && !monthPeriodService.isOpen(user.getId(), payment.getCollectedDate().getYear(), payment.getCollectedDate().getMonthValue())) {
            throw new VectisException(
                    "No se puede revertir: el mes del cobro ya está cerrado", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (payment.getRentTransaction() != null) {
            Transaction tx = payment.getRentTransaction();
            tx.setDeletedAt(now);
            transactionRepository.save(tx);
            payment.setRentTransaction(null);
        }
        if (payment.getAmortizationTransaction() != null) {
            Transaction tx = payment.getAmortizationTransaction();
            tx.setDeletedAt(now);
            transactionRepository.save(tx);
            payment.setAmortizationTransaction(null);
        }

        payment.setStatus(InvestmentPaymentStatus.PENDIENTE);
        payment.setCollectedDate(null);
        InvestmentPayment saved = paymentRepository.save(payment);
        return toResponse(saved, asset);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Transaction buildPaymentTransaction(InvestmentAsset asset, User user, Account account,
                                                 BigDecimal amount, String description, LocalDate date,
                                                 InvestmentSourceType sourceType, String currency) {
        return Transaction.builder()
                .user(user)
                .type(TransactionType.INCOME)
                .description(description)
                .amount(amount)
                .ccy(currency)
                .account(account)
                .transactionDate(date)
                .dueDate(date)
                .installment(false)
                .investmentAsset(asset)
                .investmentMovement(null)
                .investmentSourceType(sourceType)
                .build();
    }

    private void assertPending(InvestmentPayment payment) {
        if (payment.getStatus() != InvestmentPaymentStatus.PENDIENTE) {
            throw new VectisException("El pago no está pendiente", HttpStatus.CONFLICT);
        }
    }

    private void assertBondOrOn(InvestmentAsset asset) {
        if (asset.getType() != InvestmentAssetType.BONO && asset.getType() != InvestmentAssetType.ON) {
            throw new VectisException(
                    "El calendario de pagos sólo aplica a activos BONO u ON", HttpStatus.CONFLICT);
        }
    }

    private PendingPaymentResponse toPendingResponse(InvestmentPayment p) {
        InvestmentAsset asset = p.getInvestmentAsset();
        BigDecimal unitsHeld = InvestmentValuationCalculator.unitsHeldAsOf(asset, p.getCuttingDate());

        BigDecimal estimatedRent = p.getRentAmountOverride() != null
                ? p.getRentAmountOverride()
                : p.getRentPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);
        BigDecimal estimatedAmortization = p.getAmortizationAmountOverride() != null
                ? p.getAmortizationAmountOverride()
                : p.getAmortizationPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);

        return PendingPaymentResponse.builder()
                .paymentId(p.getId())
                .assetId(asset.getId())
                .assetName(asset.getName())
                .cuttingDate(p.getCuttingDate())
                .currency(p.getCurrency())
                .estimatedRent(estimatedRent)
                .estimatedAmortization(estimatedAmortization)
                .build();
    }

    private InvestmentPaymentResponse toResponse(InvestmentPayment p, InvestmentAsset asset) {
        BigDecimal unitsHeld = InvestmentValuationCalculator.unitsHeldAsOf(asset, p.getCuttingDate());

        BigDecimal estimatedRent = p.getRentAmountOverride() != null
                ? p.getRentAmountOverride()
                : p.getRentPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);
        BigDecimal estimatedAmortization = p.getAmortizationAmountOverride() != null
                ? p.getAmortizationAmountOverride()
                : p.getAmortizationPer100().multiply(unitsHeld, MC).divide(HUNDRED, MONEY_SCALE, RM);

        return InvestmentPaymentResponse.builder()
                .id(p.getId())
                .cuttingDate(p.getCuttingDate())
                .rentPer100(p.getRentPer100())
                .amortizationPer100(p.getAmortizationPer100())
                .estimatedRentAmount(estimatedRent)
                .estimatedAmortizationAmount(estimatedAmortization)
                .currency(p.getCurrency())
                .status(p.getStatus().name())
                .source(p.getSource().name())
                .userEdited(p.isUserEdited())
                .collectedDate(p.getCollectedDate())
                .rentTransactionId(p.getRentTransaction() != null ? p.getRentTransaction().getId() : null)
                .amortizationTransactionId(p.getAmortizationTransaction() != null ? p.getAmortizationTransaction().getId() : null)
                .residualAfterPer100(p.getResidualAfterPer100())
                .build();
    }
}
