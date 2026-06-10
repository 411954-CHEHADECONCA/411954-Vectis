package com.vectis.backend.service;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.domain.entity.User;
import com.vectis.backend.dto.CategoryRequest;
import com.vectis.backend.dto.CategoryResponse;
import com.vectis.backend.exception.CategoryNotFoundException;
import com.vectis.backend.mapper.CategoryMapper;
import com.vectis.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.backend.exception.VectisException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(UUID userId) {
        return categoryRepository.findAllForUser(userId)
                .stream()
                .map(categoryMapper::toResponse)
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

        return categoryMapper.toResponse(categoryRepository.save(category));
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

        return categoryMapper.toResponse(categoryRepository.save(category));
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
}
