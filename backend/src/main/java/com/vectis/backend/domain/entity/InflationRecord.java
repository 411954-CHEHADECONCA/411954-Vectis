package com.vectis.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inflation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InflationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "period_date", nullable = false, unique = true)
    private LocalDate periodDate;

    @Column(name = "monthly_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal monthlyRate;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String source = "argentinadatos.com";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
