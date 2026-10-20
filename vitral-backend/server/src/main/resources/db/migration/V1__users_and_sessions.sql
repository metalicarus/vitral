CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- One row per logged-in device. Only the SHA-256 of the token is stored,
-- so a database leak does not expose usable tokens.
CREATE TABLE sessions (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    device_name  VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX sessions_user_id_idx ON sessions (user_id);
