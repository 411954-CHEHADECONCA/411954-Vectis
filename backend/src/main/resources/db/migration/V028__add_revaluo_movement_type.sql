ALTER TABLE investment_movements
    DROP CONSTRAINT investment_movements_type_check,
    ADD  CONSTRAINT investment_movements_type_check
        CHECK (type IN ('SUSCRIPCION', 'RESCATE', 'REVALUO'));
