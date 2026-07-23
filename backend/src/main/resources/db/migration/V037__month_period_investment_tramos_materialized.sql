-- Marca de idempotencia: momento en que se materializaron los tramos de inversión
-- al cerrar este período mensual. NULL = todavía no se generaron (al reabrir se limpia).
ALTER TABLE month_periods
    ADD COLUMN investment_tramos_materialized_at TIMESTAMPTZ NULL;
