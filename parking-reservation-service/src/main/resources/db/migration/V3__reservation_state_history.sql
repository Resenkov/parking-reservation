BEGIN;

CREATE TABLE IF NOT EXISTS reservation_state_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    action TEXT NOT NULL,
    request_details TEXT,
    requested_by TEXT,
    request_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reservation_state_history_reservation_id
    ON reservation_state_history(reservation_id);

CREATE INDEX IF NOT EXISTS idx_reservation_state_history_request_date
    ON reservation_state_history(request_date);

COMMIT;
