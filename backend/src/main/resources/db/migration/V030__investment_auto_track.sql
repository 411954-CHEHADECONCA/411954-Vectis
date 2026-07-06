ALTER TABLE investment_assets
  ADD COLUMN auto_track  BOOLEAN       NOT NULL DEFAULT FALSE,
  ADD COLUMN external_id VARCHAR(255)  NULL;
