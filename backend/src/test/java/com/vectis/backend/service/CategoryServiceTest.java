package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.CategoryBudget;
import com.vectis.backend.domain.entity.CategoryType;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CategoryRequest;
import com.vectis.backend.dto.CategoryResponse;
import com.vectis.backend.exception.CategoryNotFoundException;
import com.vectis.backend.exception.VectisException;
import com.vectis.backend.mapper.CategoryMapper;
import com.vectis.backend.repository.CategoryBudgetRepository;
import com.vectis.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;

    @Mock
    private CategoryMapper categoryMapper;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("user@vectis.com")
                .fullName("Test User")
                .passwordHash("hash")
                .build();

        otherUser = User.builder()
                .id(otherId)
                .email("other@vectis.com")
                .fullName("Other User")
                .passwordHash("hash")
                .build();
    }

    // ─── getCategories ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCategories devuelve categorías globales y del usuario")
    void getCategories_returnsGlobalAndUserCategories() {
        Category global = buildCategory(null, "Alimentos", true);
        Category custom = buildCategory(user, "Gym", false);
        CategoryResponse globalResponse = buildResponse(global, null);
        CategoryResponse customResponse = buildResponse(custom, null);

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        given(categoryRepository.findAllForUser(userId)).willReturn(List.of(global, custom));
        given(categoryBudgetRepository.findAllByUser_IdAndValidFrom(userId, month)).willReturn(List.of());
        given(categoryMapper.toResponse(global, null)).willReturn(globalResponse);
        given(categoryMapper.toResponse(custom, null)).willReturn(customResponse);

        List<CategoryResponse> result = categoryService.getCategories(userId);

        assertThat(result).hasSize(2);
        verify(categoryRepository).findAllForUser(userId);
    }

    @Test
    @DisplayName("getCategories incluye el monto estimado del mes actual")
    void getCategories_includesBudgetForCurrentMonth() {
        Category custom = buildCategory(user, "Gym", false);
        CategoryBudget budget = CategoryBudget.builder()
                .id(UUID.randomUUID())
                .category(custom)
                .user(user)
                .amount(new BigDecimal("50000.0000"))
                .validFrom(LocalDate.now().withDayOfMonth(1))
                .build();
        CategoryResponse responseWithBudget = buildResponse(custom, new BigDecimal("50000.0000"));

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        given(categoryRepository.findAllForUser(userId)).willReturn(List.of(custom));
        given(categoryBudgetRepository.findAllByUser_IdAndValidFrom(userId, month)).willReturn(List.of(budget));
        given(categoryMapper.toResponse(custom, new BigDecimal("50000.0000"))).willReturn(responseWithBudget);

        List<CategoryResponse> result = categoryService.getCategories(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedAmount()).isEqualByComparingTo("50000.0000");
    }

    // ─── createCategory ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCategory persiste categoría con user_id del usuario autenticado")
    void createCategory_associatesAuthenticatedUser() {
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, null);
        Category saved = buildCategory(user, "Gym", false);
        CategoryResponse response = buildResponse(saved, null);

        given(categoryRepository.existsByNameIgnoreCaseAndUser_Id("Gym", userId)).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(saved);
        given(categoryMapper.toResponse(saved, null)).willReturn(response);

        CategoryResponse result = categoryService.createCategory(request, user);

        assertThat(result.name()).isEqualTo("Gym");
        verify(categoryRepository).save(any(Category.class));
        verify(categoryBudgetRepository, never()).save(any(CategoryBudget.class));
    }

    @Test
    @DisplayName("createCategory con monto estimado guarda el presupuesto")
    void createCategory_withEstimatedAmount_savesBudget() {
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, new BigDecimal("30000"));
        Category saved = buildCategory(user, "Gym", false);
        CategoryBudget savedBudget = CategoryBudget.builder()
                .id(UUID.randomUUID())
                .category(saved)
                .user(user)
                .amount(new BigDecimal("30000"))
                .validFrom(LocalDate.now().withDayOfMonth(1))
                .build();
        CategoryResponse response = buildResponse(saved, new BigDecimal("30000"));

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        given(categoryRepository.existsByNameIgnoreCaseAndUser_Id("Gym", userId)).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(saved);
        given(categoryBudgetRepository.findByCategory_IdAndUser_IdAndValidFrom(saved.getId(), userId, month))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedBudget));
        given(categoryBudgetRepository.save(any(CategoryBudget.class))).willReturn(savedBudget);
        given(categoryMapper.toResponse(saved, new BigDecimal("30000"))).willReturn(response);

        CategoryResponse result = categoryService.createCategory(request, user);

        assertThat(result.name()).isEqualTo("Gym");
        verify(categoryBudgetRepository).save(any(CategoryBudget.class));
    }

    @Test
    @DisplayName("createCategory con nombre duplicado para el mismo usuario lanza CONFLICT")
    void createCategory_duplicateName_throwsConflict() {
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, null);

        given(categoryRepository.existsByNameIgnoreCaseAndUser_Id("Gym", userId)).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ─── updateCategory ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCategory de categoría global lanza FORBIDDEN")
    void updateCategory_globalCategory_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Category global = buildCategory(null, "Alimentos", true);
        CategoryRequest request = new CategoryRequest("Comida", "utensils", "#10B981", CategoryType.EXPENSE, null);

        given(categoryRepository.findById(id)).willReturn(Optional.of(global));

        assertThatThrownBy(() -> categoryService.updateCategory(id, request, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateCategory de categoría de otro usuario lanza FORBIDDEN")
    void updateCategory_otherUserCategory_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Category otherCategory = buildCategory(otherUser, "Viajes", false);
        CategoryRequest request = new CategoryRequest("Viajes", "plane", "#3B82F6", CategoryType.EXPENSE, null);

        given(categoryRepository.findById(id)).willReturn(Optional.of(otherCategory));

        assertThatThrownBy(() -> categoryService.updateCategory(id, request, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateCategory de categoría inexistente lanza NOT_FOUND")
    void updateCategory_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        CategoryRequest request = new CategoryRequest("X", "x", "#000000", CategoryType.EXPENSE, null);

        given(categoryRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(id, request, user))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("updateCategory con monto estimado upserta el presupuesto del mes")
    void updateCategory_withEstimatedAmount_upsertsBudget() {
        UUID id = UUID.randomUUID();
        Category custom = buildCategory(user, "Gym", false);
        custom.setId(id);
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, new BigDecimal("45000"));
        CategoryBudget existingBudget = CategoryBudget.builder()
                .id(UUID.randomUUID())
                .category(custom)
                .user(user)
                .amount(new BigDecimal("30000"))
                .validFrom(LocalDate.now().withDayOfMonth(1))
                .build();
        CategoryResponse response = buildResponse(custom, new BigDecimal("45000"));

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        given(categoryRepository.findById(id)).willReturn(Optional.of(custom));
        given(categoryRepository.save(any(Category.class))).willReturn(custom);
        given(categoryBudgetRepository.findByCategory_IdAndUser_IdAndValidFrom(id, userId, month))
                .willReturn(Optional.of(existingBudget))
                .willReturn(Optional.of(existingBudget));
        given(categoryBudgetRepository.save(any(CategoryBudget.class))).willReturn(existingBudget);
        given(categoryMapper.toResponse(custom, new BigDecimal("45000"))).willReturn(response);

        categoryService.updateCategory(id, request, user);

        verify(categoryBudgetRepository).save(any(CategoryBudget.class));
    }

    @Test
    @DisplayName("updateCategory sin monto estimado no toca la tabla de presupuestos")
    void updateCategory_withNullEstimatedAmount_doesNotTouchBudget() {
        UUID id = UUID.randomUUID();
        Category custom = buildCategory(user, "Gym", false);
        custom.setId(id);
        CategoryRequest request = new CategoryRequest("Gym", "dumbbell", "#EC4899", CategoryType.EXPENSE, null);
        CategoryResponse response = buildResponse(custom, null);

        LocalDate month = LocalDate.now().withDayOfMonth(1);
        given(categoryRepository.findById(id)).willReturn(Optional.of(custom));
        given(categoryRepository.save(any(Category.class))).willReturn(custom);
        given(categoryBudgetRepository.findByCategory_IdAndUser_IdAndValidFrom(id, userId, month))
                .willReturn(Optional.empty());
        given(categoryMapper.toResponse(custom, null)).willReturn(response);

        categoryService.updateCategory(id, request, user);

        verify(categoryBudgetRepository, never()).save(any(CategoryBudget.class));
    }

    // ─── deleteCategory ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCategory de categoría global lanza FORBIDDEN")
    void deleteCategory_globalCategory_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Category global = buildCategory(null, "Alimentos", true);

        given(categoryRepository.findById(id)).willReturn(Optional.of(global));

        assertThatThrownBy(() -> categoryService.deleteCategory(id, user))
                .isInstanceOf(VectisException.class)
                .satisfies(ex -> assertThat(((VectisException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deleteCategory propia elimina la categoría")
    void deleteCategory_ownCategory_deletesSuccessfully() {
        UUID id = UUID.randomUUID();
        Category custom = buildCategory(user, "Gym", false);

        given(categoryRepository.findById(id)).willReturn(Optional.of(custom));

        categoryService.deleteCategory(id, user);

        verify(categoryRepository).delete(custom);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Category buildCategory(User owner, String name, boolean isDefault) {
        return Category.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .name(name)
                .icon("circle")
                .color("#9CA3AF")
                .type(CategoryType.EXPENSE)
                .isDefault(isDefault)
                .build();
    }

    private CategoryResponse buildResponse(Category c, BigDecimal estimatedAmount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getColor(), c.getType(), c.isDefault(), estimatedAmount);
    }
}
