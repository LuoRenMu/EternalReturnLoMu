CREATE TABLE IF NOT EXISTS group_pet (
 group_id TEXT NOT NULL, player_id INTEGER NOT NULL, nickname TEXT NOT NULL,
 requester_id TEXT NOT NULL, last_match_id INTEGER, last_match_time INTEGER,
 next_check_at INTEGER NOT NULL, failures INTEGER NOT NULL DEFAULT 0, last_error TEXT,
 lease_owner TEXT, lease_until INTEGER NOT NULL DEFAULT 0, expires_at INTEGER NOT NULL DEFAULT 0, inactivity_match_id BIGINT,
 expiry_notice_next_at BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(group_id,player_id));
CREATE INDEX IF NOT EXISTS group_pet_due ON group_pet(next_check_at,lease_until);
