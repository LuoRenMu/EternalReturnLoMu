ALTER TABLE eternal_return_news
    ADD COLUMN IF NOT EXISTS is_game_activity BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE eternal_return_news
SET is_game_activity = TRUE
WHERE is_redemption_code = TRUE;
