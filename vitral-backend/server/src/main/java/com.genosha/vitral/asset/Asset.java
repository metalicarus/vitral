package com.genosha.vitral.asset;

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

/** A photo or video in someone's library. The file itself lives in the library folder on disk. */
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

    /** Relative to the library root, with forward slashes. Never exposed by the API. */
    @Column(name = "relative_path", nullable = false, unique = true)
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

    public static Optional<Asset> findByOwnerAndHash(UUID ownerId, String sha256) {
        return find("ownerId = ?1 and sha256 = ?2", ownerId, sha256).firstResultOptional();
    }
}