-- Override manual del interés de un tramo (solo Cuenta Remunerada / FCI).
-- Cada movimiento abre exactamente un tramo; cuando interest_override no es NULL,
-- ese valor reemplaza la proyección por TNA del tramo (sin capitalizar al saldo).
ALTER TABLE investment_movements
    ADD COLUMN interest_override NUMERIC(19,4) NULL
        CONSTRAINT investment_movements_interest_override_check
        CHECK (interest_override IS NULL OR interest_override >= 0);
