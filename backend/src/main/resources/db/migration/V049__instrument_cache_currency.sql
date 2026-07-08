-- Moneda del instrumento, tal como la devuelve PPI SearchInstrument (hoy se descarta). Sirve para
-- preseleccionar la moneda de cobro al dar de alta un BONO/ON con seguimiento automático.
ALTER TABLE docta_instrument_cache ADD COLUMN currency VARCHAR(3);
