-- La categoría de sistema "Inversiones" (seed V007) nunca la asigna el backend: las transacciones
-- de inversión se vinculan por investment_asset_id/investment_source_type, no por categoría.
-- Sin referencias en transactions/category_budgets/recurring_movements (verificado en producción).
-- FKs son ON DELETE SET NULL/CASCADE, así que borrarla es seguro aunque algún usuario la hubiese usado.
DELETE FROM categories WHERE name = 'Inversiones' AND user_id IS NULL AND is_default = TRUE;
