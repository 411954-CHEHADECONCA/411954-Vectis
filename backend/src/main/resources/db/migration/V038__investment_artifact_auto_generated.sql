-- Marca los movimientos y valuaciones generados automáticamente al cerrar el mes del cashflow.
-- Permite eliminarlos selectivamente al reabrir el período (sin tocar los registros del usuario).
ALTER TABLE investment_movements
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE investment_valuations
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;
