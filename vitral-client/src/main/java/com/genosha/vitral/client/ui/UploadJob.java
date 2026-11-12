package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.HashCheckResult;
import com.genosha.vitral.client.api.Models.UploadResult;
import com.genosha.vitral.client.api.VitralApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sends a selection of files and folders to the server, skipping what is already there.
 *
 * <p>It works in four steps: find the photos (folders are searched inside), compute each
 * file's SHA-256, ask the server which hashes it lacks, and upload only those. Sending the
 * same folder twice, or resuming after an interruption, therefore sends nothing again.
 *
 * <p>{@link #run()} blocks, so call it off the JavaFX thread. A file the server refuses is
 * noted and the job moves on to the next one; an expired session stops the whole job.
 */
final class UploadJob {

    /** The two calls the job makes, kept apart from {@link VitralApi} so tests can stand in for the server. */
    interface Server {
        HashCheckResult check(List<String> hashes) throws IOException;

        UploadResult upload(Path file, Instant fileModifiedAt) throws IOException;
    }

    enum Phase {
        /** Searching folders for photos. */
        COLLECTING,
        /** Computing each file's SHA-256. */
        HASHING,
        /** Asking the server which files it lacks. */
        CHECKING,
        UPLOADING
    }

    /** Reports progress; called on the thread that runs the job. */
    interface Listener {
        void progress(Phase phase, int done, int total, String fileName);
    }

    record Failure(Path file, String reason) {
    }

    /**
     * @param uploaded     photos the server did not have before
     * @param alreadyThere photos the server already had, so nothing was sent for them
     * @param ignored      visible files skipped because they are not photos (by extension); hidden
     *                     files inside folders, such as caches, are skipped without being counted
     */
    record Summary(int uploaded, int alreadyThere, int ignored, List<Failure> failures, boolean cancelled) {
    }

    /** The extensions the server accepts. It checks the content itself; this only avoids hashing videos and documents. */
    static final Set<String> PHOTO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic", "heif", "webp");

    private final Server server;
    private final List<Path> selection;
    private final Listener listener;
    private volatile boolean cancelled;

    UploadJob(Server server, List<Path> selection, Listener listener) {
        this.server = server;
        this.selection = List.copyOf(selection);
        this.listener = listener;
    }

    /**
     * Stops before the next file. To also abort the file being sent, interrupt the thread
     * running the job. Safe to call from any thread.
     */
    void cancel() {
        cancelled = true;
    }

    /**
     * @throws ApiException when the server no longer accepts the session; the caller should log in again
     */
    Summary run() throws ApiException {
        listener.progress(Phase.COLLECTING, 0, 0, null);
        Collected collected = collect(selection);
        List<Failure> failures = new ArrayList<>();

        // Same content selected twice (a copy in another folder) is hashed twice but sent once.
        Map<String, Path> byHash = new LinkedHashMap<>();
        List<Path> photos = collected.photos();
        for (int i = 0; i < photos.size(); i++) {
            if (cancelled) {
                return summary(0, 0, collected, failures);
            }
            Path photo = photos.get(i);
            listener.progress(Phase.HASHING, i, photos.size(), photo.getFileName().toString());
            try {
                byHash.putIfAbsent(sha256(photo), photo);
            } catch (UncheckedIOException e) {
                failures.add(new Failure(photo, "Não foi possível ler o arquivo."));
            }
        }

        listener.progress(Phase.CHECKING, 0, 0, null);
        List<String> hashes = new ArrayList<>(byHash.keySet());
        Set<String> missing = new HashSet<>();
        for (int from = 0; from < hashes.size(); from += VitralApi.MAX_HASHES_PER_CHECK) {
            List<String> batch = hashes.subList(from, Math.min(hashes.size(), from + VitralApi.MAX_HASHES_PER_CHECK));
            try {
                missing.addAll(server.check(batch).missing());
            } catch (ApiException e) {
                if (e.isUnauthorized()) {
                    throw e;
                }
                // Without the answer, every file in the batch is sent; the server still skips duplicates.
                missing.addAll(batch);
            } catch (IOException e) {
                missing.addAll(batch);
            }
        }
        int alreadyThere = byHash.size() - missing.size();

        List<Path> toSend = byHash.entrySet().stream()
                .filter(entry -> missing.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        int uploaded = 0;
        for (int i = 0; i < toSend.size(); i++) {
            if (cancelled) {
                break;
            }
            Path photo = toSend.get(i);
            listener.progress(Phase.UPLOADING, i, toSend.size(), photo.getFileName().toString());
            try {
                UploadResult result = server.upload(photo, Files.getLastModifiedTime(photo).toInstant());
                if (result.created()) {
                    uploaded++;
                } else {
                    alreadyThere++;
                }
            } catch (ApiException e) {
                if (e.isUnauthorized()) {
                    throw e;
                }
                failures.add(new Failure(photo, reason(e)));
            } catch (InterruptedIOException e) {
                cancelled = true;
            } catch (IOException e) {
                failures.add(new Failure(photo, ErrorMessages.describe(e)));
            }
        }
        return summary(uploaded, alreadyThere, collected, failures);
    }

    private Summary summary(int uploaded, int alreadyThere, Collected collected, List<Failure> failures) {
        return new Summary(uploaded, alreadyThere, collected.ignored(), List.copyOf(failures), cancelled);
    }

    private static String reason(ApiException error) {
        return switch (error.code()) {
            case "unsupported_media_type" -> "O servidor não reconheceu o arquivo como uma foto aceita (JPEG, PNG, HEIC ou WebP).";
            default -> ErrorMessages.describe(error);
        };
    }

    /** The photos in a selection, plus how many other files were skipped. */
    record Collected(List<Path> photos, int ignored) {
    }

    /**
     * Expands folders (including subfolders) and keeps the files that look like photos, in a
     * stable order. Hidden files and folders, such as {@code .thumbnails}, are skipped.
     */
    static Collected collect(List<Path> selection) {
        List<Path> photos = new ArrayList<>();
        int ignored = 0;
        Set<Path> seen = new HashSet<>();
        for (Path item : selection) {
            List<Path> files;
            if (Files.isDirectory(item)) {
                try (Stream<Path> walk = Files.walk(item)) {
                    files = walk.filter(path -> !isHidden(item.relativize(path)))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .toList();
                } catch (IOException | UncheckedIOException e) {
                    files = List.of();
                }
            } else {
                files = List.of(item);
            }
            for (Path file : files) {
                Path normalized = file.toAbsolutePath().normalize();
                if (!seen.add(normalized)) {
                    continue;
                }
                if (isPhoto(file) && !isHidden(file.getFileName())) {
                    photos.add(file);
                } else {
                    ignored++;
                }
            }
        }
        return new Collected(photos, ignored);
    }

    private static boolean isPhoto(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && PHOTO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Whether any part of the path starts with a dot. */
    private static boolean isHidden(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** SHA-256 as 64 lowercase hex characters, read in chunks so large files never sit in memory. */
    static String sha256(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
