-- Catálogo inicial de instrumentos de renta fija disponibles para seguimiento automático.
-- Los precios (last_price, price_date) son NULL; PPI los completa en cada syncPpiValuations.
-- Para agregar nuevos instrumentos crear una migración V035+ con el mismo patrón.

INSERT INTO docta_instrument_cache (id, ticker, nombre, tipo, last_price, price_date, updated_at) VALUES

  -- ── LETRAS (LECAPs en circulación — junio 2026) ────────────────────────────
  (gen_random_uuid(), 'S30J6',  'LECAP Vto. 30/06/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S31L6',  'LECAP Vto. 31/07/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S28A6',  'LECAP Vto. 28/08/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S30S6',  'LECAP Vto. 30/09/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S30O6',  'LECAP Vto. 30/10/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S27N6',  'LECAP Vto. 27/11/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S18D6',  'LECAP Vto. 18/12/2026', 'LETRA', NULL, NULL, NOW()),
  (gen_random_uuid(), 'S31E7',  'LECAP Vto. 31/01/2027', 'LETRA', NULL, NULL, NOW()),

  -- ── BONOS soberanos ───────────────────────────────────────────────────────
  (gen_random_uuid(), 'AL29',   'BONAR USD 2029',         'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'AL30',   'BONAR USD 2030',         'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'AL35',   'BONAR USD 2035',         'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD29',   'GLOBAL USD 2029',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD30',   'GLOBAL USD 2030',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD35',   'GLOBAL USD 2035',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD38',   'GLOBAL USD 2038',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD41',   'GLOBAL USD 2041',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'GD46',   'GLOBAL USD 2046',        'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'DICP',   'DISCOUNT USD (DICP)',    'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'DICY',   'DISCOUNT ARS (DICY)',    'BONO',  NULL, NULL, NOW()),
  (gen_random_uuid(), 'BPY26',  'BONO EN PESOS 2026',    'BONO',  NULL, NULL, NOW()),

  -- ── ON (Obligaciones Negociables) ─────────────────────────────────────────
  (gen_random_uuid(), 'YCA6O',  'YPF ON Clase VI USD 2026',       'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'YCH0',   'YPF ON Clase XII USD 2030',      'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'VIST0',  'Vista Energy ON USD 2030',       'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'TLCM0',  'Telecom ON USD 2030',           'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'IRCP',   'IRSA ON USD 2028',              'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'MRCAO',  'MercadoLibre ON USD 2028',      'ON', NULL, NULL, NOW()),
  (gen_random_uuid(), 'PAMP0',  'Pampa Energía ON USD 2029',     'ON', NULL, NULL, NOW())

ON CONFLICT (ticker) DO NOTHING;
