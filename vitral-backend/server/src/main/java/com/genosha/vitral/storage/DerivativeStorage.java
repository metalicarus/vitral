package com.genosha.vitral.storage;

import com.genosha.vitral.config.VitralConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Files generated from the originals, such as thumbnails and previews:
 * {@code <data-dir>/derivatives/<user id>/<first two characters of the asset id>/<asset id>/}.
 *
 * <p>They live apart from the library, which keeps only the user's own files, and can be
 * deleted at any time: they are generated again. The two-character level keeps any single
 * folder from holding hundreds of thousands of entries.
 */
@Singleton
public class DerivativeStorage {

    private final Path root;
    private final Path temp;

    @Inject
    public DerivativeStorage(VitralConfig config) {
        this(config.dataDir().resolve("derivatives"), config.dataDir().resolve("tmp").resolve("derivatives"));
    }

    DerivativeStorage(Path root, Path temp) {
        this.root = root.toAbsolutePath().normalize();
        this.temp = temp.toAbsolutePath().normalize();
    }

    public Path thumbnail(UUID ownerId, UUID assetId) {
        return folder(ownerId, assetId).resolve("thumbnail.jpg");
    }

    public Path preview(UUID ownerId, UUID assetId) {
        return folder(ownerId, assetId).resolve("preview.jpg");
    }

    /** A scratch file on the same disk as the derivatives, so {@link #install} is an instant rename. */
    public Path newTempFile(String suffix) throws IOException {
        Files.createDirectories(temp);
        return Files.createTempFile(temp, "derivative-", suffix);
    }

    /** Puts a finished file in place in one step: readers see the old file or the new one, never half of one. */
    public void install(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException differentDisk) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path folder(UUID ownerId, UUID assetId) {
        String id = assetId.toString();
        return root.resolve(ownerId.toString()).resolve(id.substring(0, 2)).resolve(id);
    }
}