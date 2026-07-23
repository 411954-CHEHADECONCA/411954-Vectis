-- Marca de vigencia para el catálogo de instrumentos.
--
-- El catálogo (seed V034) se desactualiza: instrumentos vencen y algunos tickers son inválidos en
-- PPI (p. ej. S18D6, S30J6). En vez de mantenerlo a mano, InstrumentCatalogSyncService sincroniza
-- contra PPI y marca 'active=false' los que PPI ya no reconoce. No se borran filas para no romper
-- inversiones que referencian el ticker en investment_assets.external_id; sólo se ocultan del
-- selector (las consultas del catálogo filtran active=true).

ALTER TABLE docta_instrument_cache
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
