CREATE TABLE category_budgets (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID          NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    valid_from  DATE          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(category_id, user_id, valid_from)
);
CREATE INDEX idx_category_budgets_user_month ON category_budgets(user_id, valid_from);
