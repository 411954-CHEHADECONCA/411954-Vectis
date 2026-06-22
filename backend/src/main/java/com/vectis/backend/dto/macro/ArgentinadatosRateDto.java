package com.vectis.backend.dto.macro;

import java.math.BigDecimal;

/**
 * Deserializa la respuesta de https://api.argentinadatos.com/v1/cotizaciones/dolares/bolsa
 * Ejemplo: {"casa":"bolsa","compra":1234.56,"venta":1236.00,"fecha":"2026-06-20"}
 */
public record ArgentinadatosRateDto(
        String casa,
        BigDecimal compra,
        BigDecimal venta,
        String fecha
) {}
