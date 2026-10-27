package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Sha256;
import com.genosha.vitral.common.Timestamps;
import com.genosha.vitral.config.ServerTimeZone;
import com.genosha.vitral.media.CaptureDate;
import com.genosha.vitral.media.MediaInfo;
import com.genosha.vitral.media.MediaInspector;
import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.storage.FileName;
import com.genosha.vitral.storage.LibraryStorage;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AssetService {

    private static final Logger LOG = Logger.getLogger(AssetService.class);

    @Inject
    MediaInspector inspector;

    @Inject
    LibraryStorage storage;

    @Inject
    ServerTimeZone serverTimeZone;

    @Inject
    ThumbnailService thumbnails;

    @Inject
    DerivativeStorage derivatives;

    /** The asset and whether this upload created it ({@code false} means the owner already had it). */
    public record UploadResult(Asset asset, boolean created) {
    }

    /**
     * Catalogs an uploaded file and moves it into the library.
     *
     * <p>The slow work (reading metadata, hashing) happens before the database transaction,
     * so the database is locked only for the short duplicate check, move and insert.
     */
    public UploadResult upload(UUID ownerId, Path uploadedFile, String originalFileName, Instant fileModifiedAt) {
        MediaInfo media = inspector.inspect(uploadedFile).orElseThrow(() -> ApiException.unsupportedMediaType(
                "unsupported_media_type", "Only JPEG, PNG, HEIC/HEIF and WebP photos are accepted."));
        String sha256 = Sha256.of(uploadedFile);
        long size = sizeOf(uploadedFile);
        Instant uploadedAt = Timestamps.now();
        CaptureDate date = CaptureDate.resolve(media.capturedLocal(), media.capturedOffset(),
                Timestamps.toStoredPrecision(fileModifiedAt), uploadedAt, serverTimeZone.zone());
        String baseName = FileName.baseName(originalFileName).strip();
        String name = baseName.isEmpty() ? "photo." + media.extension() : baseName;

        UploadResult result = QuarkusTransaction.requiringNew().call(() -> {
            Optional<Asset> existing = Asset.findByOwnerAndHash(ownerId, sha256);
            if (existing.isPresent()) {
                return new UploadResult(existing.get(), false);
            }
            LibraryStorage.StoredFile stored = storage.store(uploadedFile, ownerId, date.folderDate(), name,
                    media.extension());
            try {
                Asset asset = new Asset();
                asset.ownerId = ownerId;
                asset.type = media.type();
                asset.originalFileName = name;
                asset.relativePath = stored.relativePath();
                asset.mimeType = media.mimeType();
                asset.sizeBytes = size;
                asset.sha256 = sha256;
                asset.width = media.width();
                asset.height = media.height();
                asset.takenAt = date.takenAt();
                asset.takenAtSource = date.source();
                asset.uploadedAt = uploadedAt;
                asset.thumbnailStatus = ThumbnailStatus.PENDING;
                asset.persistAndFlush();
                return new UploadResult(asset, true);
            } catch (RuntimeException e) {
                // The database rejected the row, so the file must not stay in the library either.
                storage.deleteQuietly(stored.path());
                throw e;
            }
        });
        if (result.created()) {
            // Only after the commit, so the worker is sure to find the new row.
            thumbnails.enqueue(result.asset().id);
        }
        return result;
    }

    /**
     * One of the owner's assets. Filtering by owner in the same query means another
     * user's asset is never even loaded.
     */
    public Optional<Asset> find(UUID ownerId, UUID id) {
        return Asset.find("id = ?1 and ownerId = ?2", id, ownerId).firstResultOptional();
    }

    /**
     * The asset's original file on disk. If the catalog has the asset but the file is gone
     * (deleted by hand, a disk problem), says so plainly instead of failing with a 500.
     */
    public Path originalFile(Asset asset) {
        Path file = storage.resolve(asset.relativePath);
        if (!Files.isRegularFile(file)) {
            LOG.errorf("The original of asset %s is missing from the library: %s", asset.id, file);
            throw ApiException.notFound("original_missing", "The original file is missing from the library on disk.");
        }
        return file;
    }

    /** The asset's thumbnail on disk, or a 404 saying why there is none yet. */
    public Path thumbnailFile(Asset asset) {
        return generatedFile(asset, derivatives.thumbnail(asset.ownerId, asset.id));
    }

    /** The asset's preview on disk, or a 404 saying why there is none yet. */
    public Path previewFile(Asset asset) {
        return generatedFile(asset, derivatives.preview(asset.ownerId, asset.id));
    }

    private Path generatedFile(Asset asset, Path file) {
        if (asset.thumbnailStatus == ThumbnailStatus.FAILED) {
            throw ApiException.notFound("thumbnail_failed",
                    "The thumbnail and preview could not be generated from this file.");
        }
        if (asset.thumbnailStatus == ThumbnailStatus.READY) {
            if (Files.isRegularFile(file)) {
                return file;
            }
            // Generated files may be deleted at any time; they are simply made again.
            LOG.warnf("Generated files of asset %s were missing; generating them again", asset.id);
            thumbnails.requeue(asset.id);
        }
        throw ApiException.notFound("thumbnail_pending",
                "The thumbnail and preview are still being generated; try again shortly.");
    }

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * The owner's assets, newest first by capture date, with the id breaking ties between
     * assets taken in the same millisecond. Pass the previous page's cursor to continue.
     *
     * <p>Fetches one extra row to learn whether another page exists, instead of counting.
     */
    public AssetPage list(UUID ownerId, int limit, AssetCursor after) {
        Sort newestFirst = Sort.descending("takenAt", "id");
        PanacheQuery<Asset> query = after == null
                ? Asset.find("ownerId = ?1", newestFirst, ownerId)
                : Asset.find("ownerId = ?1 and (takenAt < ?2 or (takenAt = ?2 and id < ?3))", newestFirst,
                ownerId, Instant.ofEpochMilli(after.takenAtMillis()), after.id());
        List<Asset> rows = query.page(0, limit + 1).list();

        boolean hasMore = rows.size() > limit;
        List<Asset> page = hasMore ? rows.subList(0, limit) : rows;
        Asset last = page.isEmpty() ? null : page.get(page.size() - 1);
        String nextCursor = hasMore ? new AssetCursor(last.takenAt.toEpochMilli(), last.id).encode() : null;
        return new AssetPage(page.stream().map(AssetView::of).toList(), nextCursor);
    }

    /** Largest number of hashes accepted in one check, to keep each request cheap. */
    public static final int MAX_HASHES_PER_CHECK = 1000;

    /**
     * Tells a backup client which files it still needs to send. Only the user's own assets
     * count: a photo another user has is still missing for this one.
     */
    public HashCheckResult check(UUID ownerId, List<String> hashes) {
        if (hashes == null) {
            throw ApiException.badRequest("missing_hashes", "Send the hashes in the field \"hashes\".");
        }
        if (hashes.size() > MAX_HASHES_PER_CHECK) {
            throw ApiException.badRequest("too_many_hashes",
                    "Send at most " + MAX_HASHES_PER_CHECK + " hashes per request.");
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String hash : hashes) {
            String normalized = Sha256.normalize(hash);
            if (normalized == null) {
                throw ApiException.badRequest("invalid_hash",
                        "Not a SHA-256 hash in hex (64 characters): " + abbreviate(hash));
            }
            requested.add(normalized);
        }
        if (requested.isEmpty()) {
            return new HashCheckResult(List.of(), List.of());
        }

        Map<String, UUID> found = new HashMap<>();
        for (Asset asset : Asset.<Asset>list("ownerId = ?1 and sha256 in ?2", ownerId, List.copyOf(requested))) {
            found.put(asset.sha256, asset.id);
        }
        List<HashCheckResult.Existing> existing = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String hash : requested) {
            UUID id = found.get(hash);
            if (id != null) {
                existing.add(new HashCheckResult.Existing(hash, id));
            } else {
                missing.add(hash);
            }
        }
        return new HashCheckResult(existing, missing);
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 80 ? "\"" + value + "\"" : "\"" + value.substring(0, 80) + "...\"";
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}