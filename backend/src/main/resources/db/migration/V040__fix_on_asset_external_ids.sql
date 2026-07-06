-- Corrige el external_id de las inversiones ON ya creadas con los tickers inválidos del seed V034.
--
-- V039 corrigió los tickers en el catálogo (docta_instrument_cache), pero los activos que un usuario
-- hubiera creado antes conservan el ticker viejo en investment_assets.external_id (no hay FK entre las
-- tablas). Sin esta corrección, el backfill, el sync nocturno y el pre-load de precio seguirían
-- fallando en silencio para esos activos. Se aplica el mismo mapeo que V039.
--
-- Idempotente: si no existe ningún activo con el ticker viejo, el UPDATE afecta 0 filas sin error.

UPDATE investment_assets SET external_id = 'IRCPO', updated_at = NOW()
WHERE external_id = 'IRCP'  AND type = 'ON';

UPDATE investment_assets SET external_id = 'TLCMO', updated_at = NOW()
WHERE external_id = 'TLCM0' AND type = 'ON';

UPDATE investment_assets SET external_id = 'VSCTO', updated_at = NOW()
WHERE external_id = 'VIST0' AND type = 'ON';

UPDATE investment_assets SET external_id = 'PNDCO', updated_at = NOW()
WHERE external_id = 'PAMP0' AND type = 'ON';

UPDATE investment_assets SET external_id = 'YMCJO', updated_at = NOW()
WHERE external_id = 'YCA6O' AND type = 'ON';

UPDATE investment_assets SET external_id = 'YMCIO', updated_at = NOW()
WHERE external_id = 'YCH0'  AND type = 'ON';
