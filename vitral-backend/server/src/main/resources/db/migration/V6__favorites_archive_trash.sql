-- Organizing the library: favorites, an archive for photos kept out of the timeline, and a
-- trash that keeps deleted photos for a while before they leave the catalog for good.

ALTER TABLE assets ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0 CHECK (favorite IN (0, 1));
ALTER TABLE assets ADD COLUMN archived INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1));
-- When the photo was moved to the trash, as UTC epoch milliseconds; null when it is not there.
ALTER TABLE assets ADD COLUMN trashed_at BIGINT;

-- The timeline, the most common listing, leaves out archived and trashed photos.
CREATE INDEX assets_timeline_idx ON assets (owner_id, taken_at DESC, id DESC)
    WHERE archived = 0 AND trashed_at IS NULL;
-- The periodic cleanup looks for photos trashed long ago.
CREATE INDEX assets_trashed_idx ON assets (trashed_at) WHERE trashed_at IS NOT NULL;
