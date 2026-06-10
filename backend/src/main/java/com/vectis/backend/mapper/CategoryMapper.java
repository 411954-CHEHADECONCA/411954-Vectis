package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.Category;
import com.vectis.backend.dto.CategoryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return toResponse(category, null);
    }

    public CategoryResponse toResponse(Category category, BigDecimal estimatedAmount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .type(category.getType())
                .isDefault(category.isDefault())
                .estimatedAmount(estimatedAmount)
                .build();
    }
}
