BEGIN;

CREATE INDEX IF NOT EXISTS idx_reservation_status_hold_expires_at
    ON reservation(status, hold_expires_at);

CREATE INDEX IF NOT EXISTS idx_reservation_status_arrival_deadline
    ON reservation(status, arrival_deadline);

CREATE INDEX IF NOT EXISTS idx_reservation_status_end_time
    ON reservation(status, end_time);

CREATE INDEX IF NOT EXISTS idx_reservation_spot_status_time_window
    ON reservation(spot_id, status, start_time, end_time);

COMMIT;
