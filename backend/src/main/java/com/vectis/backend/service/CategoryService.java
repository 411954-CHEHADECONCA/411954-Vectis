package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CategoryRequest;
import com.vectis.backend.dto.CategoryResponse;
import com.vectis.backend.exception.CategoryNotFoundException;
import com.vectis.backend.mapper.CategoryMapper;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.CategoryRepository;
import com.vectis.backend.repository.MonthPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.backend.exception.VectisException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final CategoryMapper categoryMapper;
    private final MonthPeriodRepository monthPeriodRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(UUID userId) {
        List<Category> categories = categoryRepository.findAllForUser(userId);

        LocalDate effectiveMonth = getEffectiveMonth(userId);
        Map<UUID, BigDecimal> budgetMap = categoryBudgetRepository
                .findLatestPerCategoryOnOrBefore(userId, effectiveMonth)
                .stream()
                .collect(Collectors.toMap(b -> b.getCategory().getId(), CategoryBudget::getAmount));

        return categories.stream()
                .map(c -> categoryMapper.toResponse(c, budgetMap.get(c.getId())))
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest request, User user) {
        if (categoryRepository.existsByNameIgnoreCaseAndUser_Id(request.name(), user.getId())) {
            throw new VectisException(
                    "Ya existe una categoría con el nombre '" + request.name() + "'",
                    HttpStatus.CONFLICT
            );
        }

        Category category = Category.builder()
                .user(user)
                .name(request.name())
                .icon(request.icon())
                .color(request.color())
                .type(request.type())
                .isDefault(false)
                .build();

        Category saved = categoryRepository.save(category);

        LocalDate budgetMonth = getEffectiveMonth(user.getId());
        if (request.estimatedAmount() != null) {
            upsertBudget(saved, user, request.estimatedAmount(), budgetMonth);
        }

        BigDecimal amount = request.estimatedAmount() != null
                ? categoryBudgetRepository.findByCategory_IdAndUser_IdAndValidFrom(saved.getId(), user.getId(), budgetMonth)
                        .map(CategoryBudget::getAmount).orElse(null)
                : null;

        return categoryMapper.toResponse(saved, amount);
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        // Categorías sistema (isDefault) son editables por cualquier usuario autenticado.
        // Categorías propias solo por su dueño. Igual que updateBudget().
        if (!category.isDefault() && !category.getUser().getId().equals(user.getId())) {
            throw new VectisException(
                    "No tenés permiso para modificar esta categoría",
                    HttpStatus.FORBIDDEN
            );
        }

        category.setName(request.name());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setType(request.type());

        Category saved = categoryRepository.save(category);

        LocalDate budgetMonth = getEffectiveMonth(user.getId());
        if (request.estimatedAmount() != null) {
            upsertBudget(saved, user, request.estimatedAmount(), budgetMonth);
        }

        BigDecimal amount = categoryBudgetRepository
                .findByCategory_IdAndUser_IdAndValidFrom(saved.getId(), user.getId(), budgetMonth)
                .map(CategoryBudget::getAmount).orElse(null);

        return categoryMapper.toResponse(saved, amount);
    }

    /** Asigna o actualiza el presupuesto para el mes efectivo. Si el mes actual está CLOSED, aplica al mes siguiente. */
    public CategoryResponse updateBudget(UUID id, BigDecimal amount, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        // Para categorías de sistema (isDefault) aceptamos cualquier usuario autenticado.
        // Para categorías personalizadas verificamos que le pertenezcan.
        if (!category.isDefault() && !category.getUser().getId().equals(user.getId())) {
            throw new VectisException("No tenés permiso para modificar esta categoría", HttpStatus.FORBIDDEN);
        }

        LocalDate effectiveMonth = getEffectiveMonth(user.getId());
        upsertBudget(category, user, amount, effectiveMonth);

        BigDecimal saved = categoryBudgetRepository
                .findByCategory_IdAndUser_IdAndValidFrom(category.getId(), user.getId(), effectiveMonth)
                .map(CategoryBudget::getAmount).orElse(null);

        return categoryMapper.toResponse(category, saved);
    }

    public void deleteCategory(UUID id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (category.isDefault() || !category.getUser().getId().equals(user.getId())) {
            throw new VectisException(
                    "No tenés permiso para eliminar esta categoría",
                    HttpStatus.FORBIDDEN
            );
        }

        categoryRepository.delete(category);
    }

    /** Devuelve el mes actual si no está cerrado; el mes siguiente si está CLOSED. */
    private LocalDate getEffectiveMonth(UUID userId) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        boolean isClosed = monthPeriodRepository
                .findByUser_IdAndYearAndMonth(userId, currentMonth.getYear(), currentMonth.getMonthValue())
                .map(mp -> "CLOSED".equals(mp.getStatus()))
                .orElse(false);
        return isClosed ? currentMonth.plusMonths(1) : currentMonth;
    }

    private void upsertBudget(Category category, User user, BigDecimal amount, LocalDate month) {
        CategoryBudget budget = categoryBudgetRepository
                .findByCategory_IdAndUser_IdAndValidFrom(category.getId(), user.getId(), month)
                .orElseGet(() -> CategoryBudget.builder()
                        .category(category)
                        .user(user)
                        .validFrom(month)
                        .build());
        budget.setAmount(amount);
        categoryBudgetRepository.save(budget);
    }
}
