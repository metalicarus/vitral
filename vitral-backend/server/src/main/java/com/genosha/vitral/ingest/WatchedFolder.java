package com.genosha.vitral.ingest;

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

/**
 * A folder on the server whose photos are cataloged where they are, for one user. Vitral
 * only reads it: files are never moved, changed or deleted, so a sync tool such as Syncthing
 * keeps working on it as usual.
 */
@Entity
@Table(name = "watched_folders")
public class WatchedFolder extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    public UUID id;

    /** Whose library the folder's photos go to. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", nullable = false, length = 36)
    public UUID ownerId;

    /** Absolute, with symbolic links resolved. */
    @Column(nullable = false, unique = true)
    public String path;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** When the last scan finished, successfully or not; null before the first one. */
    @Column(name = "last_scan_at")
    public Instant lastScanAt;

    /** Why the last scan could not run, such as the folder being missing; null when it ran. */
    @Column(name = "last_scan_error")
    public String lastScanError;
}
