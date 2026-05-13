CREATE TABLE IF NOT EXISTS reservation_policy_settings (
    id BIGINT PRIMARY KEY,
    booking_step_minutes INTEGER NOT NULL CHECK (booking_step_minutes > 0),
    hold_duration_minutes INTEGER NOT NULL CHECK (hold_duration_minutes > 0),
    arrival_deadline_minutes_before_end INTEGER NOT NULL CHECK (arrival_deadline_minutes_before_end >= 0),
    standard_cancellation_refund_percent INTEGER NOT NULL CHECK (standard_cancellation_refund_percent BETWEEN 0 AND 100),
    no_show_refund_percent INTEGER NOT NULL CHECK (no_show_refund_percent BETWEEN 0 AND 100),
    max_booking_duration_minutes INTEGER NOT NULL CHECK (max_booking_duration_minutes > 0),
    max_booking_ahead_days INTEGER NOT NULL CHECK (max_booking_ahead_days > 0)
);

INSERT INTO reservation_policy_settings (
    id,
    booking_step_minutes,
    hold_duration_minutes,
    arrival_deadline_minutes_before_end,
    standard_cancellation_refund_percent,
    no_show_refund_percent,
    max_booking_duration_minutes,
    max_booking_ahead_days
)
VALUES (
    1,
    15,
    5,
    15,
    60,
    0,
    720,
    7
)
ON CONFLICT (id) DO NOTHING;
