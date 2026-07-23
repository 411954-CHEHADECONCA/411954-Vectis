-- "Remunerada"/TNA dejan de ser campos propios de la cuenta: pasan a derivarse
-- de si existe un InvestmentAsset FCI (investment_assets.type = 'FCI') vinculado
-- vía investment_assets.account_id. Proyecto pre-producción: no se requiere backfill.
ALTER TABLE accounts
    DROP COLUMN remunerada,
    DROP COLUMN tna;
