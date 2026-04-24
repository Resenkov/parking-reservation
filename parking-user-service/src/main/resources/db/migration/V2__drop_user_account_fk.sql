ALTER TABLE IF EXISTS public.account
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);

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
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_user_email
    ON public.account (user_email);

ALTER TABLE IF EXISTS public.users
    DROP CONSTRAINT IF EXISTS fk_account;

ALTER TABLE IF EXISTS public.users
    DROP COLUMN IF EXISTS account_id;
