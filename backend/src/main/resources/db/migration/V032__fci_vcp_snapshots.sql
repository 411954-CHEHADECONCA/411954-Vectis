CREATE TABLE fci_vcp_snapshots (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  fondo        VARCHAR(255)  NOT NULL,
  categoria    VARCHAR(30)   NOT NULL,
  vcp          NUMERIC(19,4) NOT NULL,
  fecha        DATE          NOT NULL,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_fci_vcp_fondo_fecha UNIQUE (fondo, fecha)
);
CREATE INDEX idx_fci_vcp_fecha ON fci_vcp_snapshots(fecha DESC);
