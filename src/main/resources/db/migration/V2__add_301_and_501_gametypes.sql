INSERT INTO gametypes (gametype) VALUES
    ('301'),
    ('501')
ON CONFLICT (gametype) DO NOTHING;
