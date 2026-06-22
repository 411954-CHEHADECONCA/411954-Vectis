package com.vectis.backend.dto.macro;

import java.math.BigDecimal;

/**
 * Deserializa la respuesta de https://api.argentinadatos.com/v1/finanzas/indices/inflacion
 * Ejemplo: {"fecha":"2026-05-31","valor":2.4}
 */
public record ArgentinadatosInflacionDto(
        String fecha,
        BigDecimal valor
) {}
