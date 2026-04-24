BEGIN;

ALTER TABLE reservation
    DROP CONSTRAINT IF EXISTS chk_reservation_status_values;

ALTER TABLE reservation
    ALTER COLUMN status SET DEFAULT 'PENDING_HOLD';

ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_status_values
        CHECK (status IN (
            'PENDING_HOLD',
            'HOLD',
            'HOLD_FAILED',
            'CONFIRMED',
            'ACTIVE',
            'COMPLETED',
            'CANCELLED',
            'EXPIRED',
            'NO_SHOW'
        ));

COMMIT;
