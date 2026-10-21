package com.genosha.vitral.media;

import com.genosha.vitral.asset.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaInspectorTest {

    private final MediaInspector inspector = new MediaInspector();

    @TempDir
    Path dir;

    @Test
    void readsJpegDimensionsDateAndOffset() throws IOException {
        MediaInfo info = inspector.inspect(fixture("exif-sample.jpg")).orElseThrow();

        assertEquals(AssetType.PHOTO, info.type());
        assertEquals("image/jpeg", info.mimeType());
        assertEquals("jpg", info.extension());
        assertEquals(16, info.width());
        assertEquals(8, info.height());
        assertEquals(LocalDateTime.of(2024, 7, 14, 16, 32, 10), info.capturedLocal());
        assertEquals(ZoneOffset.ofHours(-3), info.capturedOffset());
    }

    @Test
    void swapsDimensionsOfPhotosTakenSideways() throws IOException {
        MediaInfo info = inspector.inspect(fixture("exif-rotated.jpg")).orElseThrow();

        assertEquals(8, info.width());
        assertEquals(16, info.height());
        assertEquals(LocalDateTime.of(2022, 1, 2, 8, 0, 0), info.capturedLocal());
        assertNull(info.capturedOffset());
    }

    @Test
    void recognizesHeicByContent() throws IOException {
        MediaInfo info = inspector.inspect(fixture("exif-sample.heic")).orElseThrow();

        assertEquals("image/heic", info.mimeType());
        assertEquals("heic", info.extension());
        assertEquals(16, info.width());
        assertEquals(8, info.height());
        assertEquals(LocalDateTime.of(2025, 3, 9, 10, 15, 0), info.capturedLocal());
        assertEquals(ZoneOffset.ofHours(1), info.capturedOffset());
    }

    @Test
    void leavesHeicSizeUnknownWithoutExifDimensions() throws IOException {
        // This file's container claims 64x64 (the encoder's padded size); the image is 16x8.
        MediaInfo info = inspector.inspect(fixture("heic-without-exif-size.heic")).orElseThrow();

        assertEquals("image/heic", info.mimeType());
        assertNull(info.width());
        assertNull(info.height());
        assertEquals(LocalDateTime.of(2025, 3, 9, 10, 15, 0), info.capturedLocal());
    }

    @Test
    void recognizesWebp() throws IOException {
        MediaInfo info = inspector.inspect(fixture("exif-sample.webp")).orElseThrow();

        assertEquals("image/webp", info.mimeType());
        assertEquals(16, info.width());
        assertEquals(8, info.height());
    }

    @Test
    void readsPngWithoutMetadata() throws IOException {
        Path png = dir.resolve("plain.png");
        ImageIO.write(new BufferedImage(5, 7, BufferedImage.TYPE_INT_RGB), "png", png.toFile());

        MediaInfo info = inspector.inspect(png).orElseThrow();

        assertEquals("image/png", info.mimeType());
        assertEquals(5, info.width());
        assertEquals(7, info.height());
        assertNull(info.capturedLocal());
    }

    @Test
    void ignoresTheExtensionAndRejectsNonPhotos() throws IOException {
        Path fake = dir.resolve("fake.jpg");
        Files.writeString(fake, "not a photo at all");

        assertTrue(inspector.inspect(fake).isEmpty());
    }

    @Test
    void parsesExifDatesDefensively() {
        assertEquals(LocalDateTime.of(2024, 7, 14, 16, 32, 10), MediaInspector.parseExifDate("2024:07:14 16:32:10"));
        assertEquals(LocalDateTime.of(2024, 7, 14, 16, 32, 10), MediaInspector.parseExifDate("2024-07-14 16:32:10"));
        assertEquals(LocalDateTime.of(2024, 7, 14, 16, 32, 10), MediaInspector.parseExifDate(" 2024:07:14 16:32:10.123 "));
        assertNull(MediaInspector.parseExifDate("0000:00:00 00:00:00"));
        assertNull(MediaInspector.parseExifDate("2024:02:30 10:00:00"));
        assertNull(MediaInspector.parseExifDate(""));
        assertNull(MediaInspector.parseExifDate(null));
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), MediaInspector.parseOffset("+05:30"));
        assertNull(MediaInspector.parseOffset("   "));
        assertNull(MediaInspector.parseOffset("local"));
    }

    private Path fixture(String name) throws IOException {
        Path copy = dir.resolve(name);
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IOException("Missing test fixture " + name);
            }
            Files.copy(in, copy);
        }
        return copy;
    }
}