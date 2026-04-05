CREATE TABLE IF NOT EXISTS public.users
(
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    account_id BIGINT UNIQUE,
    roles TEXT[] NOT NULL DEFAULT '{"USER"}'
);

CREATE TABLE IF NOT EXISTS user_roles
(
    user_id BIGINT NOT NULL,
    roles VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, roles),
    FOREIGN KEY (user_id) REFERENCES public.users(id)
);

COMMENT ON COLUMN public.users.roles IS 'Array of user roles';

INSERT INTO public.users (first_name, last_name, email, password, account_id)
VALUES
    ('John', 'Doe', 'john@example.com', 'password123', NULL),
    ('Jane', 'Smith', 'jane@example.com', 'securepass', NULL);
