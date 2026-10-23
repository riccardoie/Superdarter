INSERT INTO roles (role) VALUES
    ('USER'),
    ('ADMIN')
ON CONFLICT (role) DO NOTHING;
