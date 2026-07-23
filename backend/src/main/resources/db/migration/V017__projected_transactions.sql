ALTER TABLE month_periods DROP CONSTRAINT month_periods_status_check;
ALTER TABLE month_periods ADD CONSTRAINT month_periods_status_check
  CHECK (status IN ('OPEN', 'CLOSED', 'PROJECTED'));

ALTER TABLE transactions ADD COLUMN is_projected BOOLEAN NOT NULL DEFAULT FALSE;
