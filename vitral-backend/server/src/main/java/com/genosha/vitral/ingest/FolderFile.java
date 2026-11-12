package com.genosha.vitral.ingest;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A photo file seen in a watched folder, as it was when last hashed. When a later scan finds
 * the same size and modification time, the file is taken as unchanged and is not read again.
 */
@Entity
@Table(name = "folder_files")
public class FolderFile extends PanacheEntityBase {

    @Embeddable
    public static class Key implements Serializable {

        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "folder_id", nullable = false, length = 36)
        public UUID folderId;

        /** Relative to the folder, with forward slashes. */
        @Column(name = "relative_path", nullable = false)
        public String relativePath;

        public Key() {
        }

        public Key(UUID folderId, String relativePath) {
            this.folderId = folderId;
            this.relativePath = relativePath;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(folderId, key.folderId)
                    && Objects.equals(relativePath, key.relativePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(folderId, relativePath);
        }
    }

    @EmbeddedId
    public Key key;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(name = "modified_at", nullable = false)
    public Instant modifiedAt;

    @Column(nullable = false)
    public String sha256;

    /** The catalog entry with this content, or null when the file is not a photo Vitral accepts. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "asset_id", length = 36)
    public UUID assetId;
}
