CREATE INDEX IF NOT EXISTS group_pet_expiry_due ON group_pet(expires_at,expiry_notice_next_at,lease_until);
