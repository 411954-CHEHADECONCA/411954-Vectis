-- Agrega la fecha de vencimiento al catálogo de instrumentos de renta fija.
-- Permite autocompletar "Fecha de vencimiento" al elegir un instrumento con seguimiento automático.

ALTER TABLE docta_instrument_cache ADD COLUMN IF NOT EXISTS maturity_date DATE;

-- ── LETRAS (LECAPs — vencimiento explícito en el nombre) ─────────────────────
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-06-30' WHERE ticker = 'S30J6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-07-31' WHERE ticker = 'S31L6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-08-28' WHERE ticker = 'S28A6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-09-30' WHERE ticker = 'S30S6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-10-30' WHERE ticker = 'S30O6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-11-27' WHERE ticker = 'S27N6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-12-18' WHERE ticker = 'S18D6';
UPDATE docta_instrument_cache SET maturity_date = DATE '2027-01-31' WHERE ticker = 'S31E7';

-- ── BONOS soberanos ─────────────────────────────────────────────────────────
UPDATE docta_instrument_cache SET maturity_date = DATE '2029-07-09' WHERE ticker = 'AL29';
UPDATE docta_instrument_cache SET maturity_date = DATE '2030-07-09' WHERE ticker = 'AL30';
UPDATE docta_instrument_cache SET maturity_date = DATE '2035-07-09' WHERE ticker = 'AL35';
UPDATE docta_instrument_cache SET maturity_date = DATE '2029-07-09' WHERE ticker = 'GD29';
UPDATE docta_instrument_cache SET maturity_date = DATE '2030-07-09' WHERE ticker = 'GD30';
UPDATE docta_instrument_cache SET maturity_date = DATE '2035-07-09' WHERE ticker = 'GD35';
UPDATE docta_instrument_cache SET maturity_date = DATE '2038-01-09' WHERE ticker = 'GD38';
UPDATE docta_instrument_cache SET maturity_date = DATE '2041-07-09' WHERE ticker = 'GD41';
UPDATE docta_instrument_cache SET maturity_date = DATE '2046-07-09' WHERE ticker = 'GD46';
UPDATE docta_instrument_cache SET maturity_date = DATE '2033-12-31' WHERE ticker = 'DICP';
UPDATE docta_instrument_cache SET maturity_date = DATE '2033-12-31' WHERE ticker = 'DICY';
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-05-31' WHERE ticker = 'BPY26';

-- ── ON (Obligaciones Negociables) ───────────────────────────────────────────
UPDATE docta_instrument_cache SET maturity_date = DATE '2026-12-31' WHERE ticker = 'YCA6O';
UPDATE docta_instrument_cache SET maturity_date = DATE '2030-12-31' WHERE ticker = 'YCH0';
UPDATE docta_instrument_cache SET maturity_date = DATE '2030-12-31' WHERE ticker = 'VIST0';
UPDATE docta_instrument_cache SET maturity_date = DATE '2030-12-31' WHERE ticker = 'TLCM0';
UPDATE docta_instrument_cache SET maturity_date = DATE '2028-12-31' WHERE ticker = 'IRCP';
UPDATE docta_instrument_cache SET maturity_date = DATE '2028-12-31' WHERE ticker = 'MRCAO';
UPDATE docta_instrument_cache SET maturity_date = DATE '2029-12-31' WHERE ticker = 'PAMP0';
