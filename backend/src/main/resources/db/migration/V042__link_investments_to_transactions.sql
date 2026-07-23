-- Vincula el módulo de Inversiones con el libro de transacciones y el cashflow.
-- Suscribir / rescatar / cobrar una inversión con cuenta vinculada genera ahora
-- una fila real en `transactions`, que impacta el saldo de la cuenta y la sección
-- de inversiones del cashflow. Los revalúos nunca generan transacción.

-- Flag por inversión: permite excluir un activo puntual del flujo de caja.
-- Default TRUE => los activos existentes quedan incluidos (no es retroactivo:
-- sólo afecta movimientos futuros, según lo definido en la historia).
ALTER TABLE investment_assets
    ADD COLUMN include_in_cashflow BOOLEAN NOT NULL DEFAULT TRUE;

-- Vínculo desde la transacción hacia la inversión que la originó.
--   * investment_asset_id    : activo que generó la transacción.
--   * investment_movement_id : movimiento puntual (SUSCRIPCION/RESCATE de FCI).
--       Queda NULL para el principal inicial de Plazo Fijo y para el cobro
--       (COLLECTION), que no tienen fila propia en investment_movements.
--   * investment_source_type : discriminador del origen, necesario justamente
--       porque no siempre hay movement_id que lo desambigüe.
-- Ambos FK usan ON DELETE SET NULL: al cobrar/eliminar un activo, las
-- transacciones históricas quedan intactas pero pierden su referencia.
ALTER TABLE transactions
    ADD COLUMN investment_asset_id    UUID REFERENCES investment_assets(id)    ON DELETE SET NULL,
    ADD COLUMN investment_movement_id UUID REFERENCES investment_movements(id) ON DELETE SET NULL,
    ADD COLUMN investment_source_type VARCHAR(20)
        CONSTRAINT chk_tx_investment_source_type
        CHECK (investment_source_type IS NULL
               OR investment_source_type IN ('SUSCRIPCION', 'RESCATE', 'COLLECTION'));

-- Índice parcial: la enorme mayoría de las transacciones NO son de inversión,
-- por lo que sólo indexamos las que sí lo son (agrupación por activo en cashflow
-- y búsqueda de transacciones vinculadas al eliminar/cobrar).
CREATE INDEX idx_transactions_investment_asset_id
    ON transactions(investment_asset_id)
    WHERE investment_asset_id IS NOT NULL;
