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

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(UUID userId) {
        List<Category> categories = categoryRepository.findAllForUser(userId);

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        Map<UUID, BigDecimal> budgetMap = categoryBudgetRepository
                .findAllByUser_IdAndValidFrom(userId, month)
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

        if (request.estimatedAmount() != null) {
            upsertBudget(saved, user, request.estimatedAmount());
        }

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        BigDecimal amount = request.estimatedAmount() != null
                ? categoryBudgetRepository.findByCategory_IdAndUser_IdAndValidFrom(saved.getId(), user.getId(), month)
                        .map(CategoryBudget::getAmount).orElse(null)
                : null;

        return categoryMapper.toResponse(saved, amount);
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (category.isDefault() || !category.getUser().getId().equals(user.getId())) {
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

        if (request.estimatedAmount() != null) {
            upsertBudget(saved, user, request.estimatedAmount());
        }

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        BigDecimal amount = categoryBudgetRepository
                .findByCategory_IdAndUser_IdAndValidFrom(saved.getId(), user.getId(), month)
                .map(CategoryBudget::getAmount).orElse(null);

        return categoryMapper.toResponse(saved, amount);
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

    private void upsertBudget(Category category, User user, BigDecimal amount) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
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
