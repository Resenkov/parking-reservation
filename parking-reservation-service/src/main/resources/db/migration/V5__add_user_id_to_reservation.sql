ALTER TABLE reservation
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_reservation_user_id ON reservation(user_id);
