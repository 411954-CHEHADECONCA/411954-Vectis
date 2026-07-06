ALTER TABLE category_budgets
  ADD COLUMN account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
