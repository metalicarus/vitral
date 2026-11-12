-- Watched folders: photos cataloged where they already are, such as a folder kept in sync
-- with a phone. Vitral only reads these folders; it never moves, changes or deletes a file.

CREATE TABLE watched_folders (
                                 id              CHAR(36) NOT NULL PRIMARY KEY,
                                 owner_id        CHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Absolute and with symbolic links resolved, so the same folder cannot be added twice.
                                 path            TEXT     NOT NULL UNIQUE,
                                 created_at      BIGINT   NOT NULL,
                                 last_scan_at    BIGINT,
                                 last_scan_error TEXT
);

-- Assets gain folder_id: null for uploaded files, which live in the library, or the watched
-- folder that holds the original. relative_path is then relative to that folder. Its
-- uniqueness becomes per location, which SQLite can only change by rebuilding the table.

CREATE TABLE assets_new (
                            id                 CHAR(36) NOT NULL PRIMARY KEY,
                            owner_id           CHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                            type               TEXT     NOT NULL CHECK (type IN ('PHOTO', 'VIDEO')),
                            original_file_name TEXT     NOT NULL,
                            folder_id          CHAR(36) REFERENCES watched_folders (id) ON DELETE CASCADE,
                            relative_path      TEXT     NOT NULL,
                            mime_type          TEXT     NOT NULL,
                            size_bytes         BIGINT   NOT NULL,
                            sha256             TEXT     NOT NULL,
                            width              INTEGER,
                            height             INTEGER,
                            taken_at           BIGINT   NOT NULL,
                            taken_at_source    TEXT     NOT NULL CHECK (taken_at_source IN ('EXIF', 'FILE', 'UPLOAD')),
                            uploaded_at        BIGINT   NOT NULL,
                            thumbnail_status   TEXT     NOT NULL DEFAULT 'PENDING'
                                CHECK (thumbnail_status IN ('PENDING', 'READY', 'FAILED')),
                            thumbnail_error    TEXT,
                            UNIQUE (owner_id, sha256)
);

INSERT INTO assets_new (id, owner_id, type, original_file_name, folder_id, relative_path, mime_type, size_bytes,
                        sha256, width, height, taken_at, taken_at_source, uploaded_at, thumbnail_status,
                        thumbnail_error)
SELECT id, owner_id, type, original_file_name, NULL, relative_path, mime_type, size_bytes,
       sha256, width, height, taken_at, taken_at_source, uploaded_at, thumbnail_status, thumbnail_error
FROM assets;

DROP TABLE assets;
ALTER TABLE assets_new RENAME TO assets;

CREATE INDEX assets_owner_taken_idx ON assets (owner_id, taken_at DESC, id DESC);
CREATE INDEX assets_thumbnail_pending_idx ON assets (thumbnail_status) WHERE thumbnail_status = 'PENDING';
-- SQLite treats nulls as distinct in a unique index, so library paths need their own index.
CREATE UNIQUE INDEX assets_library_path_idx ON assets (relative_path) WHERE folder_id IS NULL;
CREATE UNIQUE INDEX assets_folder_path_idx ON assets (folder_id, relative_path) WHERE folder_id IS NOT NULL;

-- Every photo file seen in a watched folder, with the size and modification time it had
-- when it was hashed. A scan only hashes files whose size or time changed, so scanning a
-- large folder again is quick. asset_id is the catalog entry with the same content: one
-- created from this file, or one that already existed, for example uploaded by the client.
CREATE TABLE folder_files (
                              folder_id     CHAR(36) NOT NULL REFERENCES watched_folders (id) ON DELETE CASCADE,
                              relative_path TEXT     NOT NULL,
                              size_bytes    BIGINT   NOT NULL,
                              modified_at   BIGINT   NOT NULL,
                              sha256        TEXT     NOT NULL,
                              asset_id      CHAR(36) REFERENCES assets (id) ON DELETE SET NULL,
                              PRIMARY KEY (folder_id, relative_path)
);

CREATE INDEX folder_files_asset_idx ON folder_files (asset_id);
