ALTER TABLE transactions
    ALTER COLUMN type TYPE VARCHAR(20);

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_type_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('INCOME', 'EXPENSE', 'CARD_PAYMENT', 'TRANSFER'));
