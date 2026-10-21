package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Sha256;
import com.genosha.vitral.config.ServerTimeZone;
import com.genosha.vitral.media.CaptureDate;
import com.genosha.vitral.media.MediaInfo;
import com.genosha.vitral.media.MediaInspector;
import com.genosha.vitral.storage.FileName;
import com.genosha.vitral.storage.LibraryStorage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AssetService {

    @Inject
    MediaInspector inspector;

    @Inject
    LibraryStorage storage;

    @Inject
    ServerTimeZone serverTimeZone;

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
        Instant uploadedAt = Instant.now();
        CaptureDate date = CaptureDate.resolve(media.capturedLocal(), media.capturedOffset(),
                fileModifiedAt, uploadedAt, serverTimeZone.zone());
        String baseName = FileName.baseName(originalFileName).strip();
        String name = baseName.isEmpty() ? "photo." + media.extension() : baseName;

        return QuarkusTransaction.requiringNew().call(() -> {
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
                asset.persistAndFlush();
                return new UploadResult(asset, true);
            } catch (RuntimeException e) {
                // The database rejected the row, so the file must not stay in the library either.
                storage.deleteQuietly(stored.path());
                throw e;
            }
        });
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}