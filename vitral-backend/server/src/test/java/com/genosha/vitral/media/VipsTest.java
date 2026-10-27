package com.genosha.vitral.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the real libvips tools; skipped on machines where libvips is not installed. */
class VipsTest {

    private final Vips vips = new Vips(null, Duration.ofSeconds(30));

    @TempDir
    Path dir;

    @BeforeEach
    void requireLibvips() {
        assumeTrue(vips.isAvailable(), "libvips is not installed: skipping");
    }

    @Test
    void shrinksToTheLongestSide() throws Exception {
        Path big = dir.resolve("big.png");
        ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "png", big.toFile());
        Path out = dir.resolve("out.jpg");

        vips.thumbnail(big, out, 1440, 82);

        assertEquals(new PixelSize(1440, 720), jpegSize(out));
    }

    @Test
    void neverEnlarges() throws Exception {
        Path out = dir.resolve("out.jpg");

        vips.thumbnail(fixture("exif-sample.jpg"), out, 1440, 82);

        assertEquals(new PixelSize(16, 8), jpegSize(out));
    }

    @Test
    void turnsPhotosTakenSidewaysUpright() throws Exception {
        Path out = dir.resolve("out.jpg");

        // Stored as 16x8 with EXIF orientation 6 (rotate 90 degrees).
        vips.thumbnail(fixture("exif-rotated.jpg"), out, 1440, 82);

        assertEquals(new PixelSize(8, 16), jpegSize(out));
    }

    @Test
    void readsHeicAndItsRealSize() throws Exception {
        Path heic = fixture("heic-without-exif-size.heic");
        Path out = dir.resolve("out.jpg");

        vips.thumbnail(heic, out, 1440, 82);

        // The container says 64x64 (padded); the real image is 16x8.
        assertEquals(new PixelSize(16, 8), jpegSize(out));
        assertEquals(new PixelSize(16, 8), vips.size(heic));
    }

    @Test
    void removesMetadataFromTheResult() throws Exception {
        Path original = fixture("exif-sample.jpg");
        Path out = dir.resolve("out.jpg");

        vips.thumbnail(original, out, 1440, 82);

        assertTrue(ImageMetadataReader.readMetadata(original.toFile()).containsDirectoryOfType(ExifSubIFDDirectory.class));
        assertFalse(ImageMetadataReader.readMetadata(out.toFile()).containsDirectoryOfType(ExifSubIFDDirectory.class));
    }

    @Test
    void takesFileNamesLiterallyEvenWhenTheyLookLikeOptions() throws Exception {
        Path tricky = Files.copy(fixture("exif-sample.jpg"), dir.resolve("foto [Q=1,strip].jpg"));
        Path out = dir.resolve("out.jpg");

        vips.thumbnail(tricky, out, 1440, 82);

        assertEquals(new PixelSize(16, 8), jpegSize(out));
    }

    @Test
    void reportsFilesItCannotProcess() throws IOException {
        Path broken = Files.write(dir.resolve("broken.jpg"),
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5});
        Path out = dir.resolve("out.jpg");

        VipsException error = assertThrows(VipsException.class, () -> vips.thumbnail(broken, out, 1440, 82));

        assertFalse(error.getMessage().contains("VIPS-WARNING"));
    }

    @Test
    void aMissingInstallationIsNotAFileError() {
        Vips missing = new Vips(dir.resolve("no-vips-here"), Duration.ofSeconds(5));

        assertFalse(missing.isAvailable());
        assertThrows(IOException.class, () -> missing.thumbnail(dir.resolve("a.jpg"), dir.resolve("b.jpg"), 10, 80));
    }

    private static PixelSize jpegSize(Path jpeg) throws IOException {
        BufferedImage image = ImageIO.read(jpeg.toFile());
        assertTrue(image != null, "not a readable JPEG: " + jpeg);
        return new PixelSize(image.getWidth(), image.getHeight());
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