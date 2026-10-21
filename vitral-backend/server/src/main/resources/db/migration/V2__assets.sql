-- Photos and videos. The files live in <data-dir>/library; relative_path points to them.
-- Timestamps are UTC epoch milliseconds, like in V1.

CREATE TABLE assets (
                        id                 CHAR(36) NOT NULL PRIMARY KEY,
                        owner_id           CHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                        type               TEXT     NOT NULL CHECK (type IN ('PHOTO', 'VIDEO')),
                        original_file_name TEXT     NOT NULL,
                        relative_path      TEXT     NOT NULL UNIQUE,
                        mime_type          TEXT     NOT NULL,
                        size_bytes         BIGINT   NOT NULL,
                        sha256             TEXT     NOT NULL,
                        width              INTEGER,
                        height             INTEGER,
                        taken_at           BIGINT   NOT NULL,
                        taken_at_source    TEXT     NOT NULL CHECK (taken_at_source IN ('EXIF', 'FILE', 'UPLOAD')),
                        uploaded_at        BIGINT   NOT NULL,
    -- The same content is stored once per user.
                        UNIQUE (owner_id, sha256)
);

-- The timeline lists a user's assets newest first.
CREATE INDEX assets_owner_taken_idx ON assets (owner_id, taken_at DESC, id DESC);