-- Remove duplicate valuations, keeping one per (investment_id, valuation_date) using physical row order
-- ctid is used because PostgreSQL's MAX() aggregate does not accept uuid arguments
DELETE FROM investment_valuations
WHERE ctid NOT IN (
    SELECT MIN(ctid)
    FROM investment_valuations
    GROUP BY investment_id, valuation_date
);

-- Unicidad: una valuación por día por activo (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_investment_valuation_date'
    ) THEN
        ALTER TABLE investment_valuations
            ADD CONSTRAINT uq_investment_valuation_date
            UNIQUE (investment_id, valuation_date);
    END IF;
END $$;

-- Columna version para optimistic locking en JPA
ALTER TABLE investment_assets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
