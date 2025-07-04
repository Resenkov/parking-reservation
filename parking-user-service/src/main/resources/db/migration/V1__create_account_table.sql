CREATE TABLE IF NOT EXISTS public.account
(
    id BIGSERIAL PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE IF NOT EXISTS public.users
(
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL UNIQUE,
    roles TEXT[] NOT NULL DEFAULT '{"USER"}',

    CONSTRAINT fk_account
        FOREIGN KEY (account_id)
        REFERENCES public.account(id)
        ON DELETE CASCADE
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    roles VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, roles),
    FOREIGN KEY (user_id) REFERENCES users(id)
);



COMMENT ON COLUMN public.users.roles IS 'Array of user roles';

INSERT INTO public.account (balance, status)
VALUES
    (100.00, 'OPEN'),
    (50.50, 'OPEN');

INSERT INTO public.users (first_name, last_name, email, password, account_id)
VALUES
    ('John', 'Doe', 'john@example.com', 'password123', 1),
    ('Jane', 'Smith', 'jane@example.com', 'securepass', 2);