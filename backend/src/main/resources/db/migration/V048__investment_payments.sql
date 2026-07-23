-- Calendario de pagos (renta/amortización) de BONOs/ONs — una fila por fecha de corte, 1:1 con
-- flows[] de PPI Bonds/Estimate. Se persisten factores por 100 nominales + override absoluto
-- opcional; el monto real se calcula al confirmar el cobro (per_100 x nominales tenidos a la
-- fecha de corte / 100), para que comprar más nominales después no invalide el calendario.
CREATE TABLE investment_payments (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investment_asset_id             UUID NOT NULL REFERENCES investment_assets(id) ON DELETE CASCADE,
    cutting_date                    DATE NOT NULL,
    rent_per_100                    NUMERIC(19,6) NOT NULL DEFAULT 0,
    amortization_per_100            NUMERIC(19,6) NOT NULL DEFAULT 0,
    residual_after_per_100          NUMERIC(19,6),
    rent_amount_override            NUMERIC(19,4),
    amortization_amount_override    NUMERIC(19,4),
    currency                        VARCHAR(3) NOT NULL,
    status                          VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (status IN ('PENDIENTE', 'COBRADO', 'OMITIDO')),
    source                          VARCHAR(10) NOT NULL DEFAULT 'PPI'
        CHECK (source IN ('PPI', 'MANUAL')),
    user_edited                     BOOLEAN NOT NULL DEFAULT FALSE,
    collected_date                  DATE,
    rent_transaction_id             UUID REFERENCES transactions(id) ON DELETE SET NULL,
    amortization_transaction_id     UUID REFERENCES transactions(id) ON DELETE SET NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (investment_asset_id, cutting_date, source)
);

CREATE INDEX idx_investment_payments_asset ON investment_payments (investment_asset_id);

-- Índice parcial: acelera el badge (pagos PENDIENTE vencidos) y las queries de sync.
CREATE INDEX idx_investment_payments_pending_cutting_date
    ON investment_payments (cutting_date)
    WHERE status = 'PENDIENTE';

ALTER TABLE investment_assets
    ADD COLUMN payment_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    ADD COLUMN payment_currency VARCHAR(3);

-- Ensancha el CHECK de transactions.investment_source_type para las nuevas transacciones de
-- cobro de cupón/amortización (mismo patrón que V044).
ALTER TABLE transactions
    DROP CONSTRAINT chk_tx_investment_source_type;

ALTER TABLE transactions
    ADD CONSTRAINT chk_tx_investment_source_type
        CHECK (investment_source_type IS NULL
               OR investment_source_type IN ('SUSCRIPCION', 'RESCATE', 'COLLECTION_CAPITAL',
                                              'COLLECTION_YIELD', 'COUPON_RENT', 'AMORTIZATION'));
