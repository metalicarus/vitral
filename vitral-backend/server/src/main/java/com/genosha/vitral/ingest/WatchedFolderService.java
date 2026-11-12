package com.genosha.vitral.ingest;

import com.genosha.vitral.asset.Asset;
import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Timestamps;
import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.storage.OriginalFiles;
import com.genosha.vitral.user.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adds, lists and removes watched folders. Only administrators reach it, through {@link WatchedFolderResource}. */
@ApplicationScoped
public class WatchedFolderService {

    @Inject
    VitralConfig config;

    @Inject
    OriginalFiles originals;

    @Inject
    FolderWatcher watcher;

    @Inject
    FolderScanner scanner;

    @Inject
    DerivativeStorage derivatives;

    /** An asset that left the catalog with its folder, whose generated files are deleted afterwards. */
    private record Removed(UUID ownerId, UUID assetId) {
    }

    /**
     * Starts watching a folder for a user. Its photos are cataloged by a scan that starts
     * right away, in the background.
     *
     * @param requestedPath an absolute path on the server
     */
    public WatchedFolder add(String requestedPath, String ownerEmail) {
        Path folder = existingFolder(requestedPath);
        Path dataDir = realOrNormalized(config.dataDir());
        if (folder.startsWith(dataDir) || dataDir.startsWith(folder)) {
            throw ApiException.badRequest("folder_overlaps_data",
                    "The folder cannot be inside Vitral's data folder, nor contain it.");
        }
        WatchedFolder created = QuarkusTransaction.requiringNew().call(() -> {
            User owner = User.findByEmail(ownerEmail)
                    .orElseThrow(() -> ApiException.badRequest("owner_not_found", "No user has this e-mail."));
            for (WatchedFolder other : WatchedFolder.<WatchedFolder>listAll()) {
                Path otherPath = Path.of(other.path);
                if (folder.startsWith(otherPath) || otherPath.startsWith(folder)) {
                    // Overlapping folders would catalog the same files twice, possibly for two people.
                    throw ApiException.conflict("folder_overlaps",
                            "This folder is, contains or is inside a folder already being watched: " + other.path);
                }
            }
            WatchedFolder entity = new WatchedFolder();
            entity.ownerId = owner.id;
            entity.path = folder.toString();
            entity.createdAt = Timestamps.now();
            entity.persist();
            return entity;
        });
        originals.register(created.id, folder);
        watcher.requestScan(created.id, Duration.ZERO);
        return created;
    }

    public List<WatchedFolder> list() {
        return WatchedFolder.listAll(Sort.ascending("createdAt"));
    }

    public Optional<WatchedFolder> find(UUID id) {
        return Optional.ofNullable(WatchedFolder.findById(id));
    }

    /** How many photo files the last scans found in the folder. */
    public long fileCount(UUID folderId) {
        return FolderFile.count("key.folderId = ?1 and assetId is not null", folderId);
    }

    public void scanSoon(UUID folderId) {
        watcher.requestScan(folderId, Duration.ZERO);
    }

    /**
     * Stops watching the folder and takes its photos out of the catalog. The files in the
     * folder are not touched. Photos that were only linked to it, such as ones also uploaded,
     * stay in the library.
     *
     * @return false if there was no such folder
     */
    public boolean remove(UUID folderId) {
        watcher.forget(folderId);
        List<Removed> removed = scanner.betweenScans(() -> QuarkusTransaction.requiringNew().call(() -> {
            WatchedFolder folder = WatchedFolder.findById(folderId);
            if (folder == null) {
                return null;
            }
            List<Removed> assets = Asset.<Asset>list("folderId", folderId).stream()
                    .map(asset -> new Removed(asset.ownerId, asset.id))
                    .toList();
            // The database removes the folder's assets and file index along with it (ON DELETE CASCADE).
            folder.delete();
            return assets;
        }));
        if (removed == null) {
            return false;
        }
        originals.forget(folderId);
        removed.forEach(asset -> derivatives.deleteQuietly(asset.ownerId(), asset.assetId()));
        return true;
    }

    /** The folder as an absolute path with symbolic links resolved, or a 400 saying what is wrong. */
    private static Path existingFolder(String requestedPath) {
        Path path;
        try {
            path = Path.of(requestedPath.strip());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("invalid_path", "Not a valid path.");
        }
        if (!path.isAbsolute()) {
            throw ApiException.badRequest("invalid_path", "The path must be absolute, such as /photos/maria.");
        }
        if (!Files.isDirectory(path)) {
            throw ApiException.badRequest("folder_not_found", "There is no folder at this path on the server.");
        }
        if (!Files.isReadable(path)) {
            throw ApiException.badRequest("folder_not_readable", "The server cannot read this folder.");
        }
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw ApiException.badRequest("folder_not_readable", "The server cannot read this folder.");
        }
    }

    private static Path realOrNormalized(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException notCreatedYet) {
            return path.toAbsolutePath().normalize();
        }
    }
}
