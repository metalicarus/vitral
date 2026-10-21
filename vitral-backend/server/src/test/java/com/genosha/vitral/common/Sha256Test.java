package com.genosha.vitral.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Sha256Test {

    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void hashesFiles(@TempDir Path dir) throws IOException {
        Path empty = Files.createFile(dir.resolve("empty"));
        assertEquals(EMPTY, Sha256.of(empty));
    }

    @Test
    void normalizesToTheStoredForm() {
        assertEquals(EMPTY, Sha256.normalize(EMPTY));
        assertEquals(EMPTY, Sha256.normalize("  " + EMPTY.toUpperCase() + "\n"));
    }

    @Test
    void rejectsAnythingElse() {
        assertNull(Sha256.normalize(null));
        assertNull(Sha256.normalize(""));
        assertNull(Sha256.normalize(EMPTY.substring(1)));
        assertNull(Sha256.normalize(EMPTY + "0"));
        assertNull(Sha256.normalize("g" + EMPTY.substring(1)));
    }
}