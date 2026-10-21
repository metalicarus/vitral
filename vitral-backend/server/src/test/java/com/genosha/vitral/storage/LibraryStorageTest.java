package com.genosha.vitral.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LibraryStorageTest {

    private static final UUID OWNER = UUID.fromString("0b9e8f4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final LocalDate JULY = LocalDate.of(2024, 7, 14);

    @TempDir
    Path dataDir;

    @Test
    void movesTheFileIntoTheOwnersMonthFolder() throws IOException {
        LibraryStorage storage = new LibraryStorage(dataDir.resolve("library"));
        Path upload = upload("hello");

        LibraryStorage.StoredFile stored = storage.store(upload, OWNER, JULY, "IMG_1.jpg", "jpg");

        assertEquals(OWNER + "/2024/07/IMG_1.jpg", stored.relativePath());
        assertEquals("hello", Files.readString(stored.path()));
        assertFalse(Files.exists(upload));
    }

    @Test
    void neverOverwritesAnExistingFile() throws IOException {
        LibraryStorage storage = new LibraryStorage(dataDir.resolve("library"));

        LibraryStorage.StoredFile first = storage.store(upload("first"), OWNER, JULY, "IMG_1.jpg", "jpg");
        LibraryStorage.StoredFile second = storage.store(upload("second"), OWNER, JULY, "IMG_1.jpg", "jpg");

        assertEquals(OWNER + "/2024/07/IMG_1 (1).jpg", second.relativePath());
        assertEquals("first", Files.readString(first.path()));
        assertEquals("second", Files.readString(second.path()));
    }

    @Test
    void resolvesStoredPathsButRefusesToLeaveTheLibrary() {
        LibraryStorage storage = new LibraryStorage(dataDir.resolve("library"));

        assertEquals(dataDir.resolve("library/a/b.jpg").toAbsolutePath().normalize(), storage.resolve("a/b.jpg"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../vitral.db"));
    }

    private Path upload(String content) throws IOException {
        Path tmp = Files.createDirectories(dataDir.resolve("tmp"));
        return Files.writeString(Files.createTempFile(tmp, "upload", ".bin"), content);
    }
}