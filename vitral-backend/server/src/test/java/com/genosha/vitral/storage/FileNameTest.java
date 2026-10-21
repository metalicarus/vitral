package com.genosha.vitral.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNameTest {

    @Test
    void keepsNormalNames() {
        assertEquals("IMG_1234.HEIC", FileName.sanitize("IMG_1234.HEIC", "heic"));
        assertEquals("Férias na praia.jpg", FileName.sanitize("Férias na praia.jpg", "jpg"));
    }

    @Test
    void dropsDirectoriesFromBothSlashStyles() {
        assertEquals("passwd.jpg", FileName.sanitize("../../etc/passwd", "jpg"));
        assertEquals("IMG.JPG", FileName.sanitize("C:\\Users\\ana\\Fotos\\IMG.JPG", "jpg"));
        assertEquals("foto.jpg", FileName.baseName("C:\\fakepath\\foto.jpg"));
        assertEquals("", FileName.baseName(null));
    }

    @Test
    void replacesCharactersThatAreInvalidOnSomeSystems() {
        assertEquals("a_b_c_.png", FileName.sanitize("a<b>c?.png", "png"));
        assertEquals("tab_name.jpg", FileName.sanitize("tab\tname.jpg", "jpg"));
    }

    @Test
    void neverProducesEmptyHiddenOrExtensionlessNames() {
        assertEquals("photo.jpg", FileName.sanitize(null, "jpg"));
        assertEquals("photo.jpg", FileName.sanitize("   ", "jpg"));
        assertEquals("photo.jpg", FileName.sanitize("..", "jpg"));
        assertEquals("hidden.jpg", FileName.sanitize(".hidden", "jpg"));
        assertEquals("scan.png", FileName.sanitize("scan", "png"));
    }

    @Test
    void truncatesLongNamesKeepingTheExtension() {
        String name = FileName.sanitize("á".repeat(300) + ".jpeg", "jpg");

        assertTrue(name.endsWith(".jpeg"));
        assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= FileName.MAX_BYTES);
    }

    @Test
    void addsCountersBeforeTheExtension() {
        assertEquals("IMG.JPG", FileName.withCounter("IMG.JPG", 0));
        assertEquals("IMG (1).JPG", FileName.withCounter("IMG.JPG", 1));
        assertEquals("archive.tar (2).gz", FileName.withCounter("archive.tar.gz", 2));
        assertEquals("photo (3)", FileName.withCounter("photo", 3));
    }
}