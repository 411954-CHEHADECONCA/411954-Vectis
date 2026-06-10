package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    List<CreditCard> findAllByUser_IdOrderByCreatedAtAsc(UUID userId);
}
