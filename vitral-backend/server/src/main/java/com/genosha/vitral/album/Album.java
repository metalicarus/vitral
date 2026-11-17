package com.genosha.vitral.album;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** A named collection of one user's photos. Photos live on their own: deleting an album keeps them. */
@Entity
@Table(name = "albums")
public class Album extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    public UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", nullable = false, length = 36)
    public UUID ownerId;

    @Column(nullable = false, length = 100)
    public String name;

    /** The cover the user chose, or null to use the newest photo. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cover_asset_id", length = 36)
    public UUID coverAssetId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** When the album was last renamed or had photos added or removed; albums are listed by it. */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
