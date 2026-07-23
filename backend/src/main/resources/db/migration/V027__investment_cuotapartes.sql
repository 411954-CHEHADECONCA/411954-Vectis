-- Add FCI_CUOTAPARTES to the type CHECK constraint
ALTER TABLE investment_assets DROP CONSTRAINT investment_assets_type_check;
ALTER TABLE investment_assets ADD CONSTRAINT investment_assets_type_check
    CHECK (type IN ('PLAZO_FIJO','FCI','FCI_CUOTAPARTES','LETRA','BONO','ON'));

-- Add nullable units column to investment_movements (null for FCI / non-cuotapartes types)
ALTER TABLE investment_movements
    ADD COLUMN units NUMERIC(19,6) NULL
        CONSTRAINT investment_movements_units_check CHECK (units IS NULL OR units > 0);

-- Valuations table: cortes de precio por cuotaparte
CREATE TABLE investment_valuations (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    investment_id   UUID          NOT NULL REFERENCES investment_assets(id) ON DELETE CASCADE,
    valuation_date  DATE          NOT NULL,
    price_per_unit  NUMERIC(19,4) NOT NULL CHECK (price_per_unit > 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inv_valuations_investment_id ON investment_valuations(investment_id);
