ALTER TABLE recurring_movements
    ADD COLUMN card_id UUID REFERENCES credit_cards(id) ON DELETE SET NULL;

ALTER TABLE recurring_movements
    ADD CONSTRAINT chk_recurring_single_payment_method
        CHECK (account_id IS NULL OR card_id IS NULL);
