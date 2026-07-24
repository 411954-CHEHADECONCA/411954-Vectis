-- Índice de apoyo para findEarliestMovementDate (piso de navegación del cashflow):
-- MIN(CASE WHEN card IS NOT NULL THEN due_date ELSE transaction_date END) filtrando por
-- user_id, deleted_at IS NULL e is_projected = false. Índice parcial que acota el scan a los
-- movimientos reales (no proyectados, no borrados) del usuario, e incluye ambas columnas de
-- fecha para que el planner resuelva el MIN sin volver a la tabla.
CREATE INDEX IF NOT EXISTS idx_transactions_user_real_dates
    ON transactions (user_id, transaction_date, due_date)
    WHERE deleted_at IS NULL AND is_projected = false;
