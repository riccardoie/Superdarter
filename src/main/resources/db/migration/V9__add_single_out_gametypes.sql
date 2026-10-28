INSERT INTO gametypes (gametype) VALUES
    ('301 Single Out'),
    ('501 Single Out')
ON CONFLICT (gametype) DO NOTHING;
