CREATE TABLE recurring_movements (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    description  VARCHAR(200)  NOT NULL,
    amount       NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    ccy          VARCHAR(3)    NOT NULL CHECK (ccy IN ('ARS', 'USD')),
    type         VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id  UUID          REFERENCES categories(id) ON DELETE SET NULL,
    account_id   UUID          REFERENCES accounts(id)   ON DELETE SET NULL,
    day_of_month INT           NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at   TIMESTAMPTZ   NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_movements_user
    ON recurring_movements(user_id, active)
    WHERE deleted_at IS NULL;
