-- Thumbnail and preview generation. Assets that existed before this migration start as
-- PENDING, and the server generates their thumbnails on its next start.

ALTER TABLE assets ADD COLUMN thumbnail_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (thumbnail_status IN ('PENDING', 'READY', 'FAILED'));
ALTER TABLE assets ADD COLUMN thumbnail_error TEXT;

-- On startup the server looks for pending assets; this keeps that lookup quick.
CREATE INDEX assets_thumbnail_pending_idx ON assets (thumbnail_status) WHERE thumbnail_status = 'PENDING';