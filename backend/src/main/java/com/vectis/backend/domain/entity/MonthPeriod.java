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

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
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
