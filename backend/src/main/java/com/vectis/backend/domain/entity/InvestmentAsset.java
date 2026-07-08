package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "investment_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    /**
     * Cuenta de acreditación para renta/amortización de BONO/ON, cuando su moneda de pago difiere
     * de la moneda de compra del activo ({@link #currency}). Si es null, {@link #confirmPayment}
     * usa {@link #account}. Sólo aplica a BONO/ON — ver validación en {@code InvestmentService}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_account_id")
    private Account paymentAccount;

    /** Moneda en la que el instrumento paga renta/amortización (puede diferir de {@link #currency}). */
    @Column(name = "payment_currency", length = 3)
    private String paymentCurrency;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentAssetType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal tna;

    @Column(name = "auto_track", nullable = false)
    @Builder.Default
    private boolean autoTrack = false;

    @Column(name = "external_id", length = 255)
    private String externalId;

    /** Permite excluir un activo puntual del flujo de caja, independiente del flag a nivel cuenta. */
    @Builder.Default
    @Column(name = "include_in_cashflow", nullable = false)
    private boolean includeInCashflow = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvestmentAssetStatus status = InvestmentAssetStatus.ACTIVA;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "collect_date")
    private LocalDate collectDate;

    @Builder.Default
    @OneToMany(mappedBy = "investmentAsset", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("movementDate ASC, createdAt ASC")
    @BatchSize(size = 30)
    private List<InvestmentMovement> movements = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "investmentAsset", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("valuationDate ASC, createdAt ASC")
    @BatchSize(size = 30)
    private List<InvestmentValuation> valuations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
