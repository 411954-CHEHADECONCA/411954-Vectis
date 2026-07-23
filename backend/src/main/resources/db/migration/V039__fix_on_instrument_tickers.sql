-- Corrige los tickers ON del catálogo (seed V034) que eran inválidos en PPI.
--
-- Spike contra PPI (MarketData/Search, liquidación A-48HS) reveló que 6 de los 7 tickers ON
-- sembrados no existen en PPI ("Instrument not found"): al seleccionarlos, el backfill y el
-- precio quedaban vacíos y el instrumento no traía valuaciones históricas. Solo 'MRCAO' era
-- válido. Los tickers de reemplazo se verificaron con datos de precio recientes (jul-2026).
--
-- Nota: la confianza es alta para IRSA/Telecom/Vista/Pampa (emisor + serie activa); para las dos
-- ON de YPF la clase exacta no pudo confirmarse contra PPI (no expone metadata), por eso se usa un
-- nombre genérico por emisor. Los precios deben verificarse en la app.
--
-- 'MRCAO' (MercadoLibre) se mantiene: es válido aunque poco líquido (última rueda 22/05/2026); el
-- ensanche de la ventana de lookback en PpiMarketDataClient permite recuperar su último cierre.

-- IRSA: IRCP → IRCPO (corrección de sufijo O)
UPDATE docta_instrument_cache SET ticker = 'IRCPO', updated_at = NOW()
WHERE ticker = 'IRCP' AND tipo = 'ON';

-- Telecom: TLCM0 (cero) → TLCMO (letra O)
UPDATE docta_instrument_cache SET ticker = 'TLCMO', updated_at = NOW()
WHERE ticker = 'TLCM0' AND tipo = 'ON';

-- Vista Energy: VIST0 → VSCTO
UPDATE docta_instrument_cache SET ticker = 'VSCTO', updated_at = NOW()
WHERE ticker = 'VIST0' AND tipo = 'ON';

-- Pampa Energía: PAMP0 → PNDCO
UPDATE docta_instrument_cache SET ticker = 'PNDCO', updated_at = NOW()
WHERE ticker = 'PAMP0' AND tipo = 'ON';

-- YPF Clase VI: YCA6O → YMCJO (clase no confirmada — nombre genérico por emisor)
UPDATE docta_instrument_cache SET ticker = 'YMCJO', nombre = 'YPF ON USD (YMCJO)', updated_at = NOW()
WHERE ticker = 'YCA6O' AND tipo = 'ON';

-- YPF Clase XII: YCH0 → YMCIO (clase no confirmada — nombre genérico por emisor)
UPDATE docta_instrument_cache SET ticker = 'YMCIO', nombre = 'YPF ON USD (YMCIO)', updated_at = NOW()
WHERE ticker = 'YCH0' AND tipo = 'ON';
