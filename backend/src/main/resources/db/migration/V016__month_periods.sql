CREATE TABLE month_periods (
  id                        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  year                      INT         NOT NULL CHECK (year >= 2020),
  month                     INT         NOT NULL CHECK (month BETWEEN 1 AND 12),
  status                    VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                                        CHECK (status IN ('OPEN', 'CLOSED')),
  opened_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  closed_at                 TIMESTAMPTZ,
  recurring_materialized_at TIMESTAMPTZ,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_month_periods UNIQUE (user_id, year, month)
);
CREATE INDEX idx_month_periods_user_status ON month_periods (user_id, status);
