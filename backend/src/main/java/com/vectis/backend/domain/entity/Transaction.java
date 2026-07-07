package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;


@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String ccy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private CreditCard card;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "is_installment", nullable = false)
    private boolean installment;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "total_installments")
    private Integer totalInstallments;

    @Column(name = "installment_group_id")
    private UUID installmentGroupId;

    @Builder.Default
    @Column(name = "is_projected", nullable = false)
    private boolean isProjected = false;

    @Builder.Default
    @Column(name = "is_paid", nullable = false)
    private boolean paid = false;

    @Column(name = "card_payment_id")
    private UUID cardPaymentId;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Column(name = "exchange_rate_at_time", precision = 19, scale = 4)
    private BigDecimal exchangeRateAtTime;

    /**
     * Activo de inversión que originó esta transacción (null para transacciones no relacionadas).
     * ADVERTENCIA: la FK tiene ON DELETE SET NULL (migración V042). Si esta Transaction ya estaba
     * cargada en el Persistence Context de una transacción @Transactional que luego borra el
     * InvestmentAsset (ver {@code InvestmentService#collectInvestment}/{@code #deleteInvestment}),
     * este campo en memoria NO se actualiza automáticamente a null — Hibernate no re-sincroniza
     * entidades ya cargadas ante un borrado hecho por otra fila. No releer/reutilizar esta
     * asociación dentro de la misma transacción después de esos borrados.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_asset_id")
    private InvestmentAsset investmentAsset;

    /**
     * Movimiento puntual (SUSCRIPCION/RESCATE de FCI) que originó esta transacción.
     * Null para el principal inicial de Plazo Fijo y para el cobro (COLLECTION), que no
     * tienen fila propia en investment_movements — por eso se necesita {@link #investmentSourceType}.
     * Misma advertencia de ON DELETE SET NULL que {@link #investmentAsset}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_movement_id")
    private InvestmentMovement investmentMovement;

    /** Discriminador del origen de inversión (null si no aplica) — ver {@link InvestmentSourceType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_source_type", length = 20)
    private InvestmentSourceType investmentSourceType;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
