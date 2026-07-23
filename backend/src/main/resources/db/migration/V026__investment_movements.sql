-- Relax principal constraint: FCI starts at 0, recalculated from movements
ALTER TABLE investment_assets
    DROP CONSTRAINT investment_assets_principal_check,
    ADD  CONSTRAINT investment_assets_principal_check CHECK (principal >= 0);

CREATE TABLE investment_movements (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    investment_id   UUID          NOT NULL REFERENCES investment_assets(id) ON DELETE CASCADE,
    movement_date   DATE          NOT NULL,
    type            VARCHAR(15)   NOT NULL CHECK (type IN ('SUSCRIPCION', 'RESCATE')),
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inv_movements_investment_id ON investment_movements(investment_id);
