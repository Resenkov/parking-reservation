BEGIN;

ALTER TABLE reservation
    ADD COLUMN IF NOT EXISTS hold_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS arrival_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS refund_percent INTEGER NOT NULL DEFAULT 0;

UPDATE reservation
SET hold_expires_at = COALESCE(hold_expires_at, start_time),
    arrival_deadline = COALESCE(arrival_deadline, start_time + INTERVAL '15 minutes');

ALTER TABLE reservation
    DROP CONSTRAINT IF EXISTS chk_reservation_status_values;

ALTER TABLE reservation
    ALTER COLUMN status SET DEFAULT 'HOLD';

ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_status_values
        CHECK (status IN ('HOLD','CONFIRMED','ACTIVE','COMPLETED','CANCELLED','EXPIRED','NO_SHOW'));

COMMIT;
