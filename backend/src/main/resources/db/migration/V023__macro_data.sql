CREATE TABLE exchange_rates (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_type   VARCHAR(20)   NOT NULL,
    buy         NUMERIC(19,4) NOT NULL,
    sell        NUMERIC(19,4) NOT NULL,
    rate_date   DATE          NOT NULL,
    source      VARCHAR(100)  NOT NULL DEFAULT 'argentinadatos.com',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exchange_rate_type_date UNIQUE (rate_type, rate_date)
);

CREATE TABLE inflation_records (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    period_date     DATE          NOT NULL UNIQUE,
    monthly_rate    NUMERIC(8,4)  NOT NULL,
    source          VARCHAR(100)  NOT NULL DEFAULT 'argentinadatos.com',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
