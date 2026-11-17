package com.genosha.vitral.album;

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

/** A photo in an album. */
@Entity
@Table(name = "album_assets")
public class AlbumAsset extends PanacheEntityBase {

    @Embeddable
    public static class Key implements Serializable {

        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "album_id", nullable = false, length = 36)
        public UUID albumId;

        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "asset_id", nullable = false, length = 36)
        public UUID assetId;

        public Key() {
        }

        public Key(UUID albumId, UUID assetId) {
            this.albumId = albumId;
            this.assetId = assetId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(albumId, key.albumId)
                    && Objects.equals(assetId, key.assetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(albumId, assetId);
        }
    }

    @EmbeddedId
    public Key key;

    @Column(name = "added_at", nullable = false)
    public Instant addedAt;
}
