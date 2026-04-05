ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS held_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.account_operation (
    id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(255) NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    reservation_id BIGINT,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    details VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.reservation_ledger (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    held_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    captured_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    refunded_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    penalty_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    last_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_operation_email ON public.account_operation(user_email);
CREATE INDEX IF NOT EXISTS idx_account_operation_account ON public.account_operation(account_id);
