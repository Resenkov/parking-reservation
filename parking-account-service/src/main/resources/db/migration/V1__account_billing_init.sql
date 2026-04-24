CREATE TABLE IF NOT EXISTS public.account (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255),
    user_id BIGINT,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    held_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);

ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);

ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS balance NUMERIC(14,2) NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS held_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'OPEN';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'account_id'
    ) THEN
        UPDATE public.account a
        SET user_email = u.email
        FROM public.users u
        WHERE u.account_id = a.id
          AND a.user_email IS NULL;

        UPDATE public.account a
        SET user_id = u.id
        FROM public.users u
        WHERE u.account_id = a.id
          AND a.user_id IS NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_user_email
    ON public.account (user_email);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_user_id
    ON public.account (user_id);

CREATE TABLE IF NOT EXISTS public.account_operation (
    id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(255) NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    user_id BIGINT,
    account_id BIGINT NOT NULL,
    reservation_id BIGINT,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    details VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE IF EXISTS public.account_operation
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE TABLE IF NOT EXISTS public.reservation_ledger (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    user_id BIGINT,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    held_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    captured_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    refunded_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    penalty_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    last_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE IF EXISTS public.reservation_ledger
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_account_operation_email ON public.account_operation(user_email);
CREATE INDEX IF NOT EXISTS idx_account_operation_user_id ON public.account_operation(user_id);
CREATE INDEX IF NOT EXISTS idx_account_operation_account ON public.account_operation(account_id);
