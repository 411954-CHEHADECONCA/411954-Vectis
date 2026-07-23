ALTER TABLE categories ADD COLUMN is_uncategorized_default BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE categories SET is_uncategorized_default = TRUE
WHERE user_id IS NULL AND is_default = TRUE
  AND name IN ('Otros ingresos', 'Otros egresos');
