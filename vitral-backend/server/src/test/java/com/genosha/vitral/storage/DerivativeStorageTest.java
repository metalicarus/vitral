package com.genosha.vitral.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DerivativeStorageTest {

    private static final UUID OWNER = UUID.fromString("0b9e8f4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID ASSET = UUID.fromString("7c1d2a90-1c2d-4e5f-8a9b-0c1d2e3f4a5b");

    @TempDir
    Path dataDir;

    @Test
    void groupsFilesByOwnerAndAsset() {
        DerivativeStorage storage = new DerivativeStorage(dataDir.resolve("derivatives"), dataDir.resolve("tmp"));
        Path folder = dataDir.resolve("derivatives").resolve(OWNER.toString()).resolve("7c").resolve(ASSET.toString())
                .toAbsolutePath().normalize();

        assertEquals(folder.resolve("thumbnail.jpg"), storage.thumbnail(OWNER, ASSET));
        assertEquals(folder.resolve("preview.jpg"), storage.preview(OWNER, ASSET));
    }

    @Test
    void installReplacesTheOldFileInOneStep() throws IOException {
        DerivativeStorage storage = new DerivativeStorage(dataDir.resolve("derivatives"), dataDir.resolve("tmp"));
        Path target = storage.thumbnail(OWNER, ASSET);

        Path first = Files.writeString(storage.newTempFile(".jpg"), "first");
        storage.install(first, target);
        Path second = Files.writeString(storage.newTempFile(".jpg"), "second");
        storage.install(second, target);

        assertEquals("second", Files.readString(target));
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }
}