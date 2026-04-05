CREATE TABLE IF NOT EXISTS public.account (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL UNIQUE,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    held_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
);

ALTER TABLE public.account
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);

ALTER TABLE public.account
    ADD COLUMN IF NOT EXISTS held_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

ALTER TABLE public.account
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'OPEN';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.account WHERE user_email IS NULL) THEN
        ALTER TABLE public.account
            ALTER COLUMN user_email SET NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_account_user_email'
    ) THEN
        ALTER TABLE public.account
            ADD CONSTRAINT uk_account_user_email UNIQUE (user_email);
    END IF;
END $$;

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
