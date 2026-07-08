package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "docta_instrument_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctaInstrumentCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String ticker;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "last_price", precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /** Moneda del instrumento (desde PPI SearchInstrument). Preselecciona la moneda de cobro al alta. */
    @Column(length = 3)
    private String currency;

    /** Vigencia en PPI: false = delisted/inválido, se oculta del selector (InstrumentCatalogSyncService). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
