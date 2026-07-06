-- Soporte para transferencias entre cuentas propias.
-- Las dos legs (EXPENSE en origen, INCOME en destino) quedan vinculadas
-- por un UUID común, lo que permite excluirlas del cashflow y borrarlas atómicamente.
ALTER TABLE transactions
    ADD COLUMN transfer_group_id UUID;

CREATE INDEX idx_transactions_transfer_group
    ON transactions(transfer_group_id)
    WHERE transfer_group_id IS NOT NULL;
