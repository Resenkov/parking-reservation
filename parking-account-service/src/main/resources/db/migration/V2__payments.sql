CREATE TABLE IF NOT EXISTS public.payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    user_email VARCHAR(255) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,
    checkout_url VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMP,
    credited_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_provider_payment_id
    ON public.payments(provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_idempotency_key
    ON public.payments(idempotency_key);

CREATE INDEX IF NOT EXISTS idx_payments_user_email
    ON public.payments(user_email);

CREATE INDEX IF NOT EXISTS idx_payments_provider
    ON public.payments(provider);

CREATE INDEX IF NOT EXISTS idx_payments_status
    ON public.payments(status);
