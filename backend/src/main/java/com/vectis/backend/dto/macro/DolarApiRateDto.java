package com.vectis.backend.dto.macro;

import java.math.BigDecimal;

public record DolarApiRateDto(
        String casa,
        String nombre,
        BigDecimal compra,
        BigDecimal venta,
        String fechaActualizacion
) {}
