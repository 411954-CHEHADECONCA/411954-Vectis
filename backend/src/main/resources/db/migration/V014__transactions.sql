-- Libro de movimientos (transacciones). Cada cuota de una compra diferida es una
-- fila propia; las N filas comparten installment_group_id.
CREATE TABLE transactions (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                 VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    description          VARCHAR(200)  NOT NULL,
    amount               NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    ccy                  VARCHAR(3)    NOT NULL CHECK (ccy IN ('ARS', 'USD')),
    category_id          UUID          REFERENCES categories(id)    ON DELETE SET NULL,
    account_id           UUID          REFERENCES accounts(id)      ON DELETE SET NULL,
    card_id              UUID          REFERENCES credit_cards(id)  ON DELETE SET NULL,
    transaction_date     DATE          NOT NULL,
    due_date             DATE          NOT NULL,
    is_installment       BOOLEAN       NOT NULL DEFAULT FALSE,
    installment_number   INT           CHECK (installment_number >= 1),
    total_installments   INT           CHECK (total_installments >= 1),
    installment_group_id UUID,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tx_single_payment_method CHECK (account_id IS NULL OR card_id IS NULL),
    CONSTRAINT chk_tx_installment_needs_card CHECK (is_installment = FALSE OR card_id IS NOT NULL)
);

-- Índice compuesto para el listado paginado por período (user_id + due_date).
CREATE INDEX idx_transactions_user_due
    ON transactions(user_id, due_date)
    WHERE deleted_at IS NULL;

-- Índice para resolver/eliminar grupos de cuotas.
CREATE INDEX idx_transactions_group
    ON transactions(installment_group_id)
    WHERE installment_group_id IS NOT NULL;
