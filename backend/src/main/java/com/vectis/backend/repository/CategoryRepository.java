package com.vectis.backend.repository;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.user.id = :userId OR c.user IS NULL ORDER BY c.isDefault DESC, c.name ASC")
    List<Category> findAllForUser(@Param("userId") UUID userId);

    boolean existsByNameIgnoreCaseAndUser_Id(String name, UUID userId);

    /** Categoría default ("Otros ingresos"/"Otros egresos") asignada cuando un movimiento no elige categoría. */
    Optional<Category> findByTypeAndIsUncategorizedDefaultTrue(CategoryType type);
}
