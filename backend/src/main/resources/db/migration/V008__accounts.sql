CREATE TABLE accounts (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100)   NOT NULL,
    kind        VARCHAR(20)    NOT NULL CHECK (kind IN ('Banco', 'Billetera', 'Efectivo')),
    detail      VARCHAR(100),
    ccy         VARCHAR(3)     NOT NULL CHECK (ccy IN ('ARS', 'USD')),
    balance     NUMERIC(19,4)  NOT NULL DEFAULT 0,
    remunerada  BOOLEAN        NOT NULL DEFAULT FALSE,
    tna         NUMERIC(8,4),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
