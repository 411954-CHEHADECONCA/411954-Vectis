CREATE TABLE categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    icon       VARCHAR(50),
    color      VARCHAR(7),
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'BOTH')),
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_user_id ON categories(user_id);

INSERT INTO categories (name, icon, color, type, is_default) VALUES
('Alimentos',    'utensils',          '#10B981', 'EXPENSE', TRUE),
('Transporte',   'car',               '#3B82F6', 'EXPENSE', TRUE),
('Servicios',    'zap',               '#F59E0B', 'EXPENSE', TRUE),
('Suscripciones','repeat',            '#8B5CF6', 'EXPENSE', TRUE),
('Ocio',         'music',             '#EC4899', 'EXPENSE', TRUE),
('Salud',        'heart',             '#EF4444', 'EXPENSE', TRUE),
('Educacion',    'book',              '#06B6D4', 'EXPENSE', TRUE),
('Ropa',         'shirt',             '#F97316', 'EXPENSE', TRUE),
('Hogar',        'home',              '#84CC16', 'EXPENSE', TRUE),
('Sueldo',       'briefcase',         '#10B981', 'INCOME',  TRUE),
('Freelance',    'monitor',           '#3B82F6', 'INCOME',  TRUE),
('Inversiones',  'trending-up',       '#F59E0B', 'INCOME',  TRUE),
('Transferencia','arrow-right-left',  '#6B7280', 'BOTH',    TRUE),
('Otros',        'circle',            '#9CA3AF', 'BOTH',    TRUE);
