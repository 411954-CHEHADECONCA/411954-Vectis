ALTER TABLE transactions
    ADD COLUMN is_paid BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_transactions_card_period_unpaid
    ON transactions(card_id, due_date)
    WHERE is_paid = FALSE AND deleted_at IS NULL AND card_id IS NOT NULL;
