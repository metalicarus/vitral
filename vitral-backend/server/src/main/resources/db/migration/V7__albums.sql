-- Albums: named collections of a user's photos. A photo can be in many albums; deleting
-- an album never deletes its photos.

CREATE TABLE albums (
                        id             CHAR(36) NOT NULL PRIMARY KEY,
                        owner_id       CHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                        name           TEXT     NOT NULL,
    -- Chosen by the user; when null, or when that photo leaves the album, the newest photo is the cover.
                        cover_asset_id CHAR(36) REFERENCES assets (id) ON DELETE SET NULL,
                        created_at     BIGINT   NOT NULL,
                        updated_at     BIGINT   NOT NULL
);

CREATE INDEX albums_owner_idx ON albums (owner_id, updated_at DESC);

-- Which photos are in which album. A photo deleted for good leaves every album with it;
-- a photo in the trash stays here and is only hidden until it is restored.
CREATE TABLE album_assets (
                              album_id CHAR(36) NOT NULL REFERENCES albums (id) ON DELETE CASCADE,
                              asset_id CHAR(36) NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
                              added_at BIGINT   NOT NULL,
                              PRIMARY KEY (album_id, asset_id)
);

CREATE INDEX album_assets_asset_idx ON album_assets (asset_id);
