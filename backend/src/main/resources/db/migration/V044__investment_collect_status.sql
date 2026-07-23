ALTER TABLE investment_assets
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
        CONSTRAINT chk_investment_assets_status CHECK (status IN ('ACTIVA', 'COBRADA')),
    ADD COLUMN collected_at TIMESTAMPTZ,
    ADD COLUMN collect_date DATE;

ALTER TABLE transactions
    DROP CONSTRAINT chk_tx_investment_source_type;

-- Migra filas legacy con el valor 'COLLECTION' (usado por la implementación anterior de
-- collectInvestment, antes del split capital/rendimiento) al nuevo esquema. Corre con el
-- constraint viejo ya eliminado, para no violarlo con el valor nuevo antes de tiempo.
UPDATE transactions SET investment_source_type = 'COLLECTION_CAPITAL'
    WHERE investment_source_type = 'COLLECTION';

ALTER TABLE transactions
    ADD CONSTRAINT chk_tx_investment_source_type
        CHECK (investment_source_type IS NULL
               OR investment_source_type IN ('SUSCRIPCION', 'RESCATE', 'COLLECTION_CAPITAL', 'COLLECTION_YIELD'));
