package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.HashCheckResult;
import com.genosha.vitral.client.api.Models.UploadResult;
import com.genosha.vitral.client.ui.UploadJob.Summary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadJobTest {

    @TempDir
    Path folder;

    /** Stands in for the server: knows some hashes, records uploads, can refuse files by name. */
    private final class FakeServer implements UploadJob.Server {

        final Set<String> known = new HashSet<>();
        final List<String> uploaded = new ArrayList<>();
        final Set<String> refused = new HashSet<>();
        int checks;
        ApiException failEveryCall;

        @Override
        public HashCheckResult check(List<String> hashes) throws IOException {
            checks++;
            if (failEveryCall != null) {
                throw failEveryCall;
            }
            return new HashCheckResult(List.of(), hashes.stream().filter(hash -> !known.contains(hash)).toList());
        }

        @Override
        public UploadResult upload(Path file, Instant fileModifiedAt) throws IOException {
            if (refused.contains(file.getFileName().toString())) {
                throw new ApiException(415, "unsupported_media_type", "Only photos are accepted.");
            }
            uploaded.add(file.getFileName().toString());
            known.add(UploadJob.sha256(file));
            return new UploadResult(null, true);
        }
    }

    private final FakeServer server = new FakeServer();

    @Test
    void sendsOnlyWhatTheServerLacks() throws IOException {
        Path old = photo("old.jpg", "already backed up");
        photo("new.jpg", "brand new");
        server.known.add(UploadJob.sha256(old));

        Summary summary = run(folder);

        assertEquals(List.of("new.jpg"), server.uploaded);
        assertEquals(1, summary.uploaded());
        assertEquals(1, summary.alreadyThere());
    }

    @Test
    void sendingTheSameFolderAgainSendsNothing() throws IOException {
        photo("a.jpg", "a");
        photo("b.png", "b");

        run(folder);
        Summary second = run(folder);

        assertEquals(List.of("a.jpg", "b.png"), server.uploaded);
        assertEquals(0, second.uploaded());
        assertEquals(2, second.alreadyThere());
    }

    @Test
    void searchesSubfoldersAndSkipsHiddenFilesAndOtherFormats() throws IOException {
        photo("2024/julho/praia.HEIC", "praia");
        photo("2024/notas.txt", "not a photo");
        photo("2024/video.mp4", "not a photo either");
        photo(".thumbnails/cache.jpg", "hidden folder");
        photo(".oculta.jpg", "hidden file");

        Summary summary = run(folder);

        assertEquals(List.of("praia.HEIC"), server.uploaded);
        // Only the visible non-photos count; hidden files are left out without a mention.
        assertEquals(2, summary.ignored());
    }

    @Test
    void identicalCopiesAreSentOnce() throws IOException {
        photo("original.jpg", "same bytes");
        photo("copia/original (1).jpg", "same bytes");

        Summary summary = run(folder);

        assertEquals(1, server.uploaded.size());
        assertEquals(1, summary.uploaded());
    }

    @Test
    void aRefusedFileDoesNotStopTheOthers() throws IOException {
        photo("a.jpg", "a");
        photo("estragada.jpg", "corrupted");
        photo("c.jpg", "c");
        server.refused.add("estragada.jpg");

        Summary summary = run(folder);

        assertEquals(List.of("a.jpg", "c.jpg"), server.uploaded);
        assertEquals(1, summary.failures().size());
        assertEquals("estragada.jpg", summary.failures().getFirst().file().getFileName().toString());
    }

    @Test
    void anExpiredSessionStopsTheJob() throws IOException {
        photo("a.jpg", "a");
        server.failEveryCall = new ApiException(401, "not_authenticated", "Authentication is required.");

        assertThrows(ApiException.class, () -> run(folder));
        assertTrue(server.uploaded.isEmpty());
    }

    @Test
    void checksInBatchesTheServerAccepts() throws IOException {
        for (int i = 0; i < 1001; i++) {
            photo("foto_" + i + ".jpg", "content " + i);
        }

        Summary summary = run(folder);

        assertEquals(2, server.checks);
        assertEquals(1001, summary.uploaded());
    }

    @Test
    void cancellingBeforeStartingSendsNothing() throws IOException {
        photo("a.jpg", "a");
        UploadJob job = new UploadJob(server, List.of(folder), (phase, done, total, name) -> { });
        job.cancel();

        Summary summary = job.run();

        assertTrue(summary.cancelled());
        assertTrue(server.uploaded.isEmpty());
    }

    @Test
    void filesChosenOneByOneWork() throws IOException {
        Path single = photo("sozinha.webp", "single");

        Summary summary = run(single);

        assertEquals(List.of("sozinha.webp"), server.uploaded);
        assertFalse(summary.cancelled());
    }

    private Summary run(Path selection) throws ApiException {
        return new UploadJob(server, List.of(selection), (phase, done, total, name) -> { }).run();
    }

    private Path photo(String relative, String content) throws IOException {
        Path file = folder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
