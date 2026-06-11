package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.RecurringMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringMovementRepository extends JpaRepository<RecurringMovement, UUID> {

    List<RecurringMovement> findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID userId);

    Optional<RecurringMovement> findByIdAndDeletedAtIsNull(UUID id);
}
