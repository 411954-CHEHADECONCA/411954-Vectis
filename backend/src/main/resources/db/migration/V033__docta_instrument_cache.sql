CREATE TABLE docta_instrument_cache (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  ticker       VARCHAR(20)   NOT NULL,
  nombre       VARCHAR(255)  NOT NULL,
  tipo         VARCHAR(10)   NOT NULL,
  last_price   NUMERIC(19,4),
  price_date   DATE,
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_docta_ticker UNIQUE (ticker)
);
CREATE INDEX idx_docta_tipo ON docta_instrument_cache(tipo);
