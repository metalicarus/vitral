-- An administrator can disable an account: it can no longer log in and its sessions end,
-- but its photos, albums and watched folders stay as they are, ready if it is enabled again.

ALTER TABLE users ADD COLUMN disabled INTEGER NOT NULL DEFAULT 0 CHECK (disabled IN (0, 1));
