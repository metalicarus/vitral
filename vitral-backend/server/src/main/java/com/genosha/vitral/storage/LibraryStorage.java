package com.genosha.vitral.storage;

import com.genosha.vitral.config.VitralConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The photo library on disk: {@code <data-dir>/library/<user id>/<year>/<month>/<file>}.
 *
 * <p>Files keep their original names, so the library stays readable without Vitral.
 * Name clashes get a counter, as in {@code IMG_1234 (1).HEIC}. Originals are never
 * overwritten.
 */
@Singleton
public class LibraryStorage {

    private static final int MAX_NAME_ATTEMPTS = 1000;

    private final Path root;

    @Inject
    public LibraryStorage(VitralConfig config) {
        this(config.dataDir().resolve("library"));
    }

    LibraryStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Where a file ended up: its absolute path and its path relative to the library root. */
    public record StoredFile(Path path, String relativePath) {
    }

    /**
     * Moves {@code source} into the owner's folder for {@code date}. The source must be on
     * the same disk for the move to be instant; otherwise it is copied.
     */
    public StoredFile store(Path source, UUID ownerId, LocalDate date, String originalName,
                            String fallbackExtension) throws IOException {
        Path directory = root.resolve(ownerId.toString())
                .resolve("%04d".formatted(date.getYear()))
                .resolve("%02d".formatted(date.getMonthValue()));
        Files.createDirectories(directory);

        String name = FileName.sanitize(originalName, fallbackExtension);
        for (int counter = 0; counter < MAX_NAME_ATTEMPTS; counter++) {
            Path target = directory.resolve(FileName.withCounter(name, counter));
            try {
                // Creating the file first reserves the name atomically, even with
                // concurrent uploads; the move below then replaces this empty placeholder.
                Files.createFile(target);
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
            try {
                move(source, target);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(target);
                throw e;
            }
            return new StoredFile(target, relativize(target));
        }
        throw new IOException("No free file name for " + name + " in " + directory);
    }

    /** Resolves a stored relative path, refusing anything that would escape the library. */
    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the library: " + relativePath);
        }
        return path;
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: an orphan file is harmless and can be cleaned up later.
        }
    }

    private String relativize(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException differentDisk) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(source);
        }
    }
}