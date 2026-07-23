-- Índice para las consultas de frescura del refresh on-demand de mercado
-- (existsBySourceAndValuationDate / findMaxValuationDateBySource).
CREATE INDEX IF NOT EXISTS idx_investment_valuations_source_date
    ON investment_valuations (source, valuation_date);
