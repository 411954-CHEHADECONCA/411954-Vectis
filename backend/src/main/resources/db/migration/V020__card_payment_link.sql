ALTER TABLE transactions
    ADD COLUMN card_payment_id UUID REFERENCES transactions(id);

CREATE INDEX idx_transactions_card_payment_id
    ON transactions(card_payment_id)
    WHERE card_payment_id IS NOT NULL;
