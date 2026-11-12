package com.genosha.vitral.ingest;

import com.genosha.vitral.asset.Asset;
import com.genosha.vitral.asset.ThumbnailService;
import com.genosha.vitral.common.Sha256;
import com.genosha.vitral.common.Timestamps;
import com.genosha.vitral.config.ServerTimeZone;
import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.media.CaptureDate;
import com.genosha.vitral.media.MediaInfo;
import com.genosha.vitral.media.MediaInspector;
import com.genosha.vitral.storage.OriginalFiles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Catalogs the photos of a watched folder where they are.
 *
 * <p>A scan walks the folder and, for each photo file:
 * <ul>
 *   <li>skips it when its size and modification time match the last scan, without reading it;</li>
 *   <li>otherwise hashes it, and links it to the owner's asset with the same content if there
 *       is one (an upload, or the same file under its old name), or catalogs it as a new asset.</li>
 * </ul>
 * A file renamed inside the folder keeps its asset, which just points to the new name. A file
 * changed in place updates its asset. A file deleted from the folder stays in the catalog.
 *
 * <p>The folder is only read: nothing in it is ever created, changed, moved or deleted.
 * Scans run one at a time, and each file is written to the database in its own short
 * transaction, after the slow reading and hashing.
 */
@ApplicationScoped
public class FolderScanner {

    private static final Logger LOG = Logger.getLogger(FolderScanner.class);

    /** The formats the server accepts; the content is checked too, this only avoids reading other files. */
    static final Set<String> PHOTO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "heif", "webp");
    private static final int DELETE_BATCH = 500;

    /**
     * What a scan did, for the logs and for tests.
     *
     * @param deferred files left for a later scan because they changed too recently, and may
     *                 still be being written
     */
    public record ScanResult(int seen, int added, int linked, int updated, int moved, int unsupported, int failed,
                             int deferred, boolean ran) {

        static ScanResult notRun() {
            return new ScanResult(0, 0, 0, 0, 0, 0, 0, 0, false);
        }

        boolean changedSomething() {
            return added + linked + updated + moved + unsupported + failed > 0;
        }
    }

    @Inject
    MediaInspector inspector;

    @Inject
    ServerTimeZone serverTimeZone;

    @Inject
    ThumbnailService thumbnails;

    @Inject
    OriginalFiles originals;

    @Inject
    VitralConfig config;

    private final Object lock = new Object();

    /** Scans the folder now and waits for it to finish. */
    public ScanResult scan(UUID folderId) {
        return scan(folderId, directory -> { });
    }

    /**
     * @param onDirectory told about every folder visited, so changes inside it can be watched
     */
    public ScanResult scan(UUID folderId, Consumer<Path> onDirectory) {
        synchronized (lock) {
            WatchedFolder folder = QuarkusTransaction.requiringNew().call(() -> WatchedFolder.findById(folderId));
            if (folder == null) {
                return ScanResult.notRun();
            }
            Path root = Path.of(folder.path);
            if (!Files.isDirectory(root) || !Files.isReadable(root)) {
                // Probably a disk that is not mounted; the catalog is left as it is.
                LOG.warnf("Watched folder %s cannot be read; skipping this scan", root);
                finished(folderId, "The folder does not exist or cannot be read.");
                return ScanResult.notRun();
            }
            Scan scan = new Scan(folder, root, onDirectory);
            ScanResult result = scan.run();
            finished(folderId, null);
            if (result.changedSomething()) {
                LOG.infof("Scanned %s: %d photo(s) seen, %d added, %d already in the library, %d updated, %d moved,"
                                + " %d not accepted, %d unreadable", root, result.seen(), result.added(), result.linked(),
                        result.updated(), result.moved(), result.unsupported(), result.failed());
            }
            return result;
        }
    }

    /** Runs {@code work} between scans, so it never changes a folder while one is being scanned. */
    public <T> T betweenScans(Supplier<T> work) {
        synchronized (lock) {
            return work.get();
        }
    }

    private void finished(UUID folderId, String error) {
        QuarkusTransaction.requiringNew().run(() -> {
            WatchedFolder folder = WatchedFolder.findById(folderId);
            if (folder != null) {
                folder.lastScanAt = Timestamps.now();
                folder.lastScanError = error;
            }
        });
    }

    /** One pass over one folder. */
    private final class Scan {

        private final WatchedFolder folder;
        private final Path root;
        private final Consumer<Path> onDirectory;
        private final Map<String, FolderFile> known = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private final Instant settledBefore;
        private int added;
        private int linked;
        private int updated;
        private int moved;
        private int unsupported;
        private int failed;
        private int deferred;

        Scan(WatchedFolder folder, Path root, Consumer<Path> onDirectory) {
            this.folder = folder;
            this.root = root;
            this.onDirectory = onDirectory;
            this.settledBefore = Instant.now().minus(config.imports().settle());
        }

        ScanResult run() {
            QuarkusTransaction.requiringNew().run(() -> FolderFile.<FolderFile>list("key.folderId", folder.id)
                    .forEach(file -> known.put(file.key.relativePath, file)));
            boolean complete = walk();
            if (complete) {
                forgetVanishedFiles();
            }
            return new ScanResult(seen.size(), added, linked, updated, moved, unsupported, failed, deferred, true);
        }

        /** @return whether the whole folder could be walked */
        private boolean walk() {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(root) && isHidden(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        onDirectory.accept(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && !isHidden(file) && isPhotoName(file)) {
                            visit(file, attrs);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        LOG.warnf("Cannot read %s in a watched folder: %s", file, e.getMessage());
                        failed++;
                        return FileVisitResult.CONTINUE;
                    }
                });
                return failed == 0;
            } catch (IOException | UncheckedIOException e) {
                LOG.errorf(e, "Scanning %s stopped half-way", root);
                return false;
            }
        }

        private void visit(Path file, BasicFileAttributes attrs) {
            String relativePath = root.relativize(file).toString().replace('\\', '/');
            seen.add(relativePath);
            long size = attrs.size();
            Instant modifiedAt = Timestamps.toStoredPrecision(attrs.lastModifiedTime().toInstant());
            FolderFile entry = known.get(relativePath);
            if (entry != null && entry.sizeBytes == size && entry.modifiedAt.equals(modifiedAt)) {
                return;
            }
            if (modifiedAt.isAfter(settledBefore)) {
                // Possibly still being written; the watcher schedules another scan for it.
                deferred++;
                return;
            }
            try {
                String sha256 = Sha256.of(file);
                Optional<MediaInfo> media = inspector.inspect(file);
                catalog(file, relativePath, size, modifiedAt, sha256, media.orElse(null));
            } catch (UncheckedIOException e) {
                LOG.warnf("Cannot read %s in a watched folder: %s", file, e.getMessage());
                failed++;
            }
        }

        /** Links the file to an asset, creating, updating or moving one as needed, and records it. */
        private void catalog(Path file, String relativePath, long size, Instant modifiedAt, String sha256,
                             MediaInfo media) {
            UUID ownerId = folder.ownerId;
            List<UUID> toThumbnail = new ArrayList<>();
            QuarkusTransaction.requiringNew().run(() -> {
                Asset sameContent = Asset.findByOwnerAndHash(ownerId, sha256).orElse(null);
                Asset atThisPath = Asset.<Asset>find("folderId = ?1 and relativePath = ?2", folder.id, relativePath)
                        .firstResult();
                UUID assetId = null;
                if (media == null) {
                    unsupported++;
                } else if (sameContent != null) {
                    assetId = sameContent.id;
                    if (sameContent == atThisPath) {
                        // Same content as before, only the timestamp changed.
                    } else if (folder.id.equals(sameContent.folderId) && atThisPath == null
                            && !Files.exists(originals.resolve(sameContent.folderId, sameContent.relativePath))) {
                        sameContent.relativePath = relativePath;
                        sameContent.originalFileName = file.getFileName().toString();
                        moved++;
                    } else {
                        linked++;
                    }
                } else if (atThisPath != null) {
                    atThisPath.describe(media, sha256, size, date(media, modifiedAt));
                    assetId = atThisPath.id;
                    toThumbnail.add(atThisPath.id);
                    updated++;
                } else {
                    Asset asset = new Asset();
                    asset.ownerId = ownerId;
                    asset.folderId = folder.id;
                    asset.relativePath = relativePath;
                    asset.originalFileName = file.getFileName().toString();
                    asset.uploadedAt = Timestamps.now();
                    asset.describe(media, sha256, size, date(media, modifiedAt));
                    asset.persist();
                    assetId = asset.id;
                    toThumbnail.add(asset.id);
                    added++;
                }
                record(relativePath, size, modifiedAt, sha256, assetId);
            });
            // Only after the commit, so the worker is sure to find the row.
            toThumbnail.forEach(thumbnails::enqueue);
        }

        private CaptureDate date(MediaInfo media, Instant modifiedAt) {
            return CaptureDate.resolve(media.capturedLocal(), media.capturedOffset(), modifiedAt, Timestamps.now(),
                    serverTimeZone.zone());
        }

        private void record(String relativePath, long size, Instant modifiedAt, String sha256, UUID assetId) {
            FolderFile entry = FolderFile.findById(new FolderFile.Key(folder.id, relativePath));
            boolean isNew = entry == null;
            if (isNew) {
                entry = new FolderFile();
                entry.key = new FolderFile.Key(folder.id, relativePath);
            }
            entry.sizeBytes = size;
            entry.modifiedAt = modifiedAt;
            entry.sha256 = sha256;
            entry.assetId = assetId;
            if (isNew) {
                // Persisted once filled in: Hibernate builds the INSERT from the state at this point.
                entry.persist();
            }
            known.put(relativePath, entry);
        }

        /**
         * Drops index rows of files no longer in the folder. Only the index: their assets stay
         * in the catalog. Runs only after a complete walk, so an unreadable subfolder does not
         * make its files look deleted.
         */
        private void forgetVanishedFiles() {
            List<String> vanished = known.keySet().stream().filter(path -> !seen.contains(path)).toList();
            for (int from = 0; from < vanished.size(); from += DELETE_BATCH) {
                List<String> batch = vanished.subList(from, Math.min(vanished.size(), from + DELETE_BATCH));
                QuarkusTransaction.requiringNew().run(() ->
                        FolderFile.delete("key.folderId = ?1 and key.relativePath in ?2", folder.id, batch));
            }
        }
    }

    static boolean isPhotoName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && PHOTO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Hidden files and folders, such as .stfolder or Syncthing's .syncthing.*.tmp files, are left alone. */
    static boolean isHidden(Path path) {
        return path.getFileName().toString().startsWith(".");
    }
}
