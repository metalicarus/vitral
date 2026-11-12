package com.genosha.vitral.asset;

import com.genosha.vitral.media.CameraSettings;
import com.genosha.vitral.media.CaptureDate;
import com.genosha.vitral.media.Coordinates;
import com.genosha.vitral.media.MediaInfo;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A photo or video in someone's library. The file itself lives on disk: in the library
 * folder when it was uploaded, or where it already was when it came from a watched folder.
 */
@Entity
@Table(name = "assets")
public class Asset extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    public UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", nullable = false, length = 36)
    public UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AssetType type;

    @Column(name = "original_file_name", nullable = false)
    public String originalFileName;

    /** The watched folder that holds the original, or null when it was uploaded into the library. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "folder_id", length = 36)
    public UUID folderId;

    /**
     * Where the original is, with forward slashes: relative to the watched folder when
     * {@link #folderId} is set, otherwise to the library root. Never exposed by the API.
     */
    @Column(name = "relative_path", nullable = false)
    public String relativePath;

    @Column(name = "mime_type", nullable = false)
    public String mimeType;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(nullable = false)
    public String sha256;

    public Integer width;

    public Integer height;

    @Column(name = "taken_at", nullable = false)
    public Instant takenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "taken_at_source", nullable = false)
    public TakenAtSource takenAtSource;

    @Column(name = "uploaded_at", nullable = false)
    public Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "thumbnail_status", nullable = false)
    public ThumbnailStatus thumbnailStatus;

    /** Why thumbnail generation failed, for the logs and for support. Never exposed by the API. */
    @Column(name = "thumbnail_error")
    public String thumbnailError;

    @Column(name = "camera_make")
    public String cameraMake;

    @Column(name = "camera_model")
    public String cameraModel;

    @Column(name = "lens_model")
    public String lensModel;

    /** Aperture, such as 4.0 for f/4. */
    @Column(name = "f_number")
    public Double fNumber;

    /** Shutter time in seconds, such as 0.004 for 1/250 s. */
    @Column(name = "exposure_seconds")
    public Double exposureSeconds;

    public Integer iso;

    /** Millimeters. */
    @Column(name = "focal_length")
    public Double focalLength;

    /** Decimal degrees, negative to the south; null when the photo has no GPS position. */
    public Double latitude;

    /** Decimal degrees, negative to the west. */
    public Double longitude;

    /**
     * Which version of the metadata reading filled in this row. When the reading learns new
     * fields, {@link #METADATA_VERSION} goes up and older rows are read again in the background.
     */
    @Column(name = "metadata_version", nullable = false)
    public int metadataVersion;

    /** The current version of the metadata reading; see {@link #metadataVersion}. */
    public static final int METADATA_VERSION = 1;

    public static Optional<Asset> findByOwnerAndHash(UUID ownerId, String sha256) {
        return find("ownerId = ?1 and sha256 = ?2", ownerId, sha256).firstResultOptional();
    }

    /**
     * Fills in what is known about the file's content, and sends it back to the thumbnail
     * queue. Used for new assets and when the file of a watched folder changes.
     */
    public void describe(MediaInfo media, String sha256, long sizeBytes, CaptureDate date) {
        this.type = media.type();
        this.mimeType = media.mimeType();
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.width = media.width();
        this.height = media.height();
        this.takenAt = date.takenAt();
        this.takenAtSource = date.source();
        this.thumbnailStatus = ThumbnailStatus.PENDING;
        this.thumbnailError = null;
        describeCapture(media);
    }

    /** Fills in the camera, exposure and position, and marks the row as read by the current version. */
    public void describeCapture(MediaInfo media) {
        CameraSettings camera = media.camera();
        this.cameraMake = camera.make();
        this.cameraModel = camera.model();
        this.lensModel = camera.lens();
        this.fNumber = camera.fNumber();
        this.exposureSeconds = camera.exposureSeconds();
        this.iso = camera.iso();
        this.focalLength = camera.focalLength();
        Coordinates location = media.location();
        this.latitude = location == null ? null : location.latitude();
        this.longitude = location == null ? null : location.longitude();
        this.metadataVersion = METADATA_VERSION;
    }
}