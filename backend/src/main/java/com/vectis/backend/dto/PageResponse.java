package com.vectis.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Contrato estándar de paginación para listados de la API. Desacopla la respuesta
 * de la implementación de Spring Data {@link Page}.
 */
@Schema(description = "Página de resultados con metadatos de paginación")
public record PageResponse<T>(

        @Schema(description = "Elementos de la página actual")
        List<T> content,

        @Schema(description = "Índice de página (base 0)", example = "0")
        int page,

        @Schema(description = "Tamaño de página", example = "20")
        int size,

        @Schema(description = "Total de elementos que matchean los filtros", example = "42")
        long totalElements,

        @Schema(description = "Total de páginas", example = "3")
        int totalPages,

        @Schema(description = "Indica si hay una página siguiente", example = "true")
        boolean hasNext
) {

    /** Construye la respuesta a partir de un {@link Page} y su contenido ya mapeado a DTO. */
    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
