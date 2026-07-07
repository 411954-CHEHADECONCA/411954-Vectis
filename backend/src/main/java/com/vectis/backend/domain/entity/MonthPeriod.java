package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "month_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Nombre entre backticks: fuerza a Hibernate a citar esta columna en cualquier dialecto.
     * "year"/"month" son funciones built-in de H2 (YEAR(), MONTH()) — sin comillas, el CREATE TABLE
     * de test falla silenciosamente. Citar sólo estas dos columnas (en vez de activar
     * `globally_quoted_identifiers` para todo el esquema) evita que ese ajuste global rompa cualquier
     * query nativa futura por el case-folding de H2 en identificadores sin comillas
     * (ver FciVcpSnapshotRepositoryTest).
     */
    @Column(name = "`year`", nullable = false)
    private int year;

    @Column(name = "`month`", nullable = false)
    private int month;

    /** "OPEN" or "CLOSED" */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "recurring_materialized_at")
    private OffsetDateTime recurringMaterializedAt;

    /** Momento en que se materializaron los tramos de inversión al cerrar este mes. Null = aún no. */
    @Column(name = "investment_tramos_materialized_at")
    private OffsetDateTime investmentTramosMaterializedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (openedAt == null) {
            openedAt = createdAt;
        }
        if (status == null) {
            status = "OPEN";
        }
    }
}
