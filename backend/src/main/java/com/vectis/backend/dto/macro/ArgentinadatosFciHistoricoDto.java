package com.vectis.backend.dto.macro;

import java.math.BigDecimal;

/**
 * Deserializa cada ítem de la serie histórica de un FCI:
 * https://api.argentinadatos.com/v1/finanzas/fci/fondos/{slug}/historico
 *
 * El endpoint devuelve más campos (slug, fondoId, patrimonio, retornos, etc.) que se ignoran;
 * sólo interesan el nombre del fondo, la fecha y el valor de cuotaparte de ese día.
 */
public record ArgentinadatosFciHistoricoDto(
        String nombre,
        String fecha,
        BigDecimal valorCuotaparte,
        String categoriaKey
) {}
