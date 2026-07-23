package com.vectis.backend.dto.macro;

import java.util.List;

/**
 * Wrapper para la nueva estructura de respuesta del endpoint
 * {@code /finanzas/fci/fondos/{slug}/historico} de argentinadatos (migración API 2026-07).
 * El array histórico pasó de devolverse directamente a estar anidado bajo el campo "historico".
 */
public record ArgentinadatosFciHistoricoResponseDto(
        String nombre,
        List<ArgentinadatosFciHistoricoDto> historico
) {
    public List<ArgentinadatosFciHistoricoDto> historico() {
        return historico != null ? historico : List.of();
    }
}
