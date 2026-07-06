CREATE TABLE investment_assets (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255)   NOT NULL,
    type           VARCHAR(20)    NOT NULL CHECK (type IN ('FCI', 'PLAZO_FIJO', 'LETRA', 'BONO', 'ON')),
    currency       VARCHAR(3)     NOT NULL CHECK (currency IN ('ARS', 'USD')),
    principal      NUMERIC(19,4)  NOT NULL CHECK (principal > 0),
    purchase_date  DATE           NOT NULL,
    maturity_date  DATE,
    tna            NUMERIC(8,4)   NOT NULL CHECK (tna >= 0),
    account_id     UUID           REFERENCES accounts(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investment_assets_user_id ON investment_assets(user_id);
