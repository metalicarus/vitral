package com.genosha.vitral.asset;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.genosha.vitral.support.TestAssets.ownerId;
import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestAssets.waitForThumbnails;
import static com.genosha.vitral.support.TestPhotos.brokenJpeg;
import static com.genosha.vitral.support.TestPhotos.fixture;
import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Thumbnails and previews are generated in the background after each upload. */
@QuarkusTest
class ThumbnailPipelineTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    ThumbnailService thumbnails;

    @Inject
    DerivativeStorage derivatives;

    @BeforeEach
    void requireLibvips() {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
    }

    @Test
    void generatesAPreviewAndAThumbnail() throws Exception {
        String token = testUsers.tokenFor("thumbs@example.com");
        String id = given().auth().oauth2(token)
                .multiPart("file", "wide.png", png(2000, 1000, 0x3366CC), "image/png")
                .post(ASSETS)
                .then().statusCode(201)
                .body("thumbnailStatus", is("PENDING"))
                .extract().path("id");

        assertEquals("READY", waitForThumbnails(token, id).getString("thumbnailStatus"));
        UUID owner = ownerId(token);
        assertEquals(1440, ImageIO.read(derivatives.preview(owner, UUID.fromString(id)).toFile()).getWidth());
        BufferedImage thumbnail = ImageIO.read(derivatives.thumbnail(owner, UUID.fromString(id)).toFile());
        assertEquals(360, thumbnail.getWidth());
        assertEquals(180, thumbnail.getHeight());
    }

    @Test
    void theGeneratedFilesCarryNoMetadata() throws Exception {
        String token = testUsers.tokenFor("thumbs-private@example.com");
        String id = upload(token, fixture("exif-sample.jpg"), "dated.jpg");

        waitForThumbnails(token, id);

        Path preview = derivatives.preview(ownerId(token), UUID.fromString(id));
        assertFalse(ImageMetadataReader.readMetadata(preview.toFile()).containsDirectoryOfType(ExifSubIFDDirectory.class));
    }

    @Test
    void fillsInTheSizeOfHeicFilesWithoutExifDimensions() {
        String token = testUsers.tokenFor("thumbs-heic@example.com");
        String id = given().auth().oauth2(token)
                .multiPart("file", "phone.heic", fixture("heic-without-exif-size.heic"))
                .post(ASSETS)
                .then().statusCode(201)
                .body("width", nullValue())
                .extract().path("id");

        JsonPath asset = waitForThumbnails(token, id);

        assertEquals("READY", asset.getString("thumbnailStatus"));
        assertEquals(16, asset.getInt("width"));
        assertEquals(8, asset.getInt("height"));
    }

    @Test
    void marksFilesLibvipsCannotReadAsFailed() {
        String token = testUsers.tokenFor("thumbs-broken@example.com");
        String id = upload(token, brokenJpeg(), "broken.jpg");

        assertEquals("FAILED", waitForThumbnails(token, id).getString("thumbnailStatus"));
        assertFalse(Files.exists(derivatives.thumbnail(ownerId(token), UUID.fromString(id))));
    }
}