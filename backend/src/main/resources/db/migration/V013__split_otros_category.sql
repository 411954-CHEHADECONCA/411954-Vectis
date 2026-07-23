UPDATE categories
SET name = 'Otros egresos',
    type = 'EXPENSE'
WHERE name = 'Otros'
  AND type = 'BOTH'
  AND is_default = TRUE
  AND user_id IS NULL;

INSERT INTO categories (name, icon, color, type, is_default)
VALUES ('Otros ingresos', 'circle', '#9CA3AF', 'INCOME', TRUE);
