-- SQLite schema. Ids are UUIDs stored as text; timestamps are UTC epoch milliseconds.
-- Column types are spelled the way Hibernate expects them (CHAR for ids, BIGINT for
-- Long), so its schema validation in dev mode matches. SQLite stores CHAR as text
-- and BIGINT as an integer either way.

CREATE TABLE users (
                       id            CHAR(36) NOT NULL PRIMARY KEY,
                       email         TEXT     NOT NULL UNIQUE,
                       display_name  TEXT     NOT NULL,
                       password_hash TEXT     NOT NULL,
                       role          TEXT     NOT NULL CHECK (role IN ('ADMIN', 'USER')),
                       created_at    BIGINT   NOT NULL
);

-- One row per logged-in device. Only the SHA-256 of the token is stored,
-- so a database leak does not expose usable tokens.
CREATE TABLE sessions (
                          id           CHAR(36) NOT NULL PRIMARY KEY,
                          user_id      CHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                          token_hash   TEXT     NOT NULL UNIQUE,
                          device_name  TEXT,
                          created_at   BIGINT   NOT NULL,
                          last_used_at BIGINT   NOT NULL,
                          expires_at   BIGINT   NOT NULL
);

CREATE INDEX sessions_user_id_idx ON sessions (user_id);