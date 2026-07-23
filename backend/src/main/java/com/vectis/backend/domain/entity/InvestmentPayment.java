package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Una fila del calendario de pagos (renta + amortización) de un BONO/ON — 1:1 con un {@code flow}
 * de PPI {@code Bonds/Estimate} (fuente {@code PPI}) o cargada a mano por el usuario (fuente
 * {@code MANUAL}). Se persisten factores <b>por 100 nominales</b> ({@link #rentPer100},
 * {@link #amortizationPer100}); el monto real a cobrar se calcula al confirmar el pago como
 * {@code per100 × nominalesTenidosALaFecha / 100}, para que comprar más nominales después de creado
 * el calendario no lo invalide.
 *
 * <p>{@link #rentAmountOverride}/{@link #amortizationAmountOverride} permiten al usuario fijar un
 * monto absoluto (comisiones/retenciones reales) que reemplaza el cálculo por factor.
 */
@Entity
@Table(name = "investment_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_asset_id", nullable = false)
    private InvestmentAsset investmentAsset;

    @Column(name = "cutting_date", nullable = false)
    private LocalDate cuttingDate;

    @Builder.Default
    @Column(name = "rent_per_100", nullable = false, precision = 19, scale = 6)
    private BigDecimal rentPer100 = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "amortization_per_100", nullable = false, precision = 19, scale = 6)
    private BigDecimal amortizationPer100 = BigDecimal.ZERO;

    /** Residual (fracción de 100 nominales) que queda DESPUÉS de este pago. 0 marca el pago final. */
    @Column(name = "residual_after_per_100", precision = 19, scale = 6)
    private BigDecimal residualAfterPer100;

    @Column(name = "rent_amount_override", precision = 19, scale = 4)
    private BigDecimal rentAmountOverride;

    @Column(name = "amortization_amount_override", precision = 19, scale = 4)
    private BigDecimal amortizationAmountOverride;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private InvestmentPaymentStatus status = InvestmentPaymentStatus.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private InvestmentPaymentSource source = InvestmentPaymentSource.PPI;

    @Builder.Default
    @Column(name = "user_edited", nullable = false)
    private boolean userEdited = false;

    @Column(name = "collected_date")
    private LocalDate collectedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rent_transaction_id")
    private Transaction rentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amortization_transaction_id")
    private Transaction amortizationTransaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
