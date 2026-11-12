-- How each photo was taken (camera, lens, exposure) and where (GPS), read from its EXIF.
-- All optional: many photos, such as screenshots or edited exports, carry none of it.

ALTER TABLE assets ADD COLUMN camera_make TEXT;
ALTER TABLE assets ADD COLUMN camera_model TEXT;
ALTER TABLE assets ADD COLUMN lens_model TEXT;
-- Aperture, such as 4.0 for f/4.
ALTER TABLE assets ADD COLUMN f_number REAL;
-- Shutter time in seconds, such as 0.004 for 1/250 s.
ALTER TABLE assets ADD COLUMN exposure_seconds REAL;
ALTER TABLE assets ADD COLUMN iso INTEGER;
-- Millimeters.
ALTER TABLE assets ADD COLUMN focal_length REAL;
-- Decimal degrees; negative latitudes are south, negative longitudes west.
ALTER TABLE assets ADD COLUMN latitude REAL;
ALTER TABLE assets ADD COLUMN longitude REAL;

-- Which version of the metadata reading filled in the row. Rows from before this migration
-- start at 0, and the server reads their files again in the background on its next start.
ALTER TABLE assets ADD COLUMN metadata_version INTEGER NOT NULL DEFAULT 0;
CREATE INDEX assets_metadata_version_idx ON assets (metadata_version);
