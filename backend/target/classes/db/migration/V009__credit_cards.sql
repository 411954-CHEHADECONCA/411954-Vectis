CREATE TABLE credit_cards (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank         VARCHAR(100)  NOT NULL,
    network      VARCHAR(20)   NOT NULL CHECK (network IN ('Visa', 'Mastercard', 'Amex')),
    last4        VARCHAR(4)    NOT NULL CHECK (last4 ~ '^\d{4}$'),
    ccy          VARCHAR(3)    NOT NULL CHECK (ccy IN ('ARS', 'USD')),
    credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    closing_day  INTEGER       NOT NULL CHECK (closing_day BETWEEN 1 AND 31),
    due_day      INTEGER       NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    accent       VARCHAR(20)   NOT NULL DEFAULT '#52eacd',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_cards_user_id ON credit_cards(user_id);
