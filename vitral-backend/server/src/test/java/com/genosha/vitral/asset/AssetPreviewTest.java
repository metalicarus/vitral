package com.genosha.vitral.asset;

import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.genosha.vitral.support.TestAssets.ownerId;
import static com.genosha.vitral.support.TestAssets.pendingAssetOf;
import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestAssets.waitForThumbnails;
import static com.genosha.vitral.support.TestPhotos.brokenJpeg;
import static com.genosha.vitral.support.TestPhotos.png;
import static com.genosha.vitral.support.TestPhotos.sha256;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** GET /api/v1/assets/{id}/preview: the large JPEG for showing one photo full screen. */
@QuarkusTest
class AssetPreviewTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    ThumbnailService thumbnails;

    @Inject
    DerivativeStorage derivatives;

    @Test
    void sendsThePreviewOnceItIsReady() throws IOException {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("preview-ready@example.com");
        byte[] photo = png(2000, 1000, 0xFF8C00);
        String id = upload(token, photo, "Pôr do sol.png");
        waitForThumbnails(token, id);
        String etag = "\"" + sha256(photo) + "-preview-v" + ThumbnailService.GENERATION_VERSION + "\"";

        byte[] jpeg = given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(200)
                .contentType("image/jpeg")
                .header("ETag", is(etag))
                .header("Cache-Control", is("private, max-age=2592000"))
                .header("X-Content-Type-Options", is("nosniff"))
                .header("Content-Disposition", containsString("-preview.jpg"))
                .extract().asByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(1440, image.getWidth());
        assertEquals(720, image.getHeight());

        given().auth().oauth2(token).header("If-None-Match", etag)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(304);
    }

    @Test
    void thePreviewAndTheThumbnailHaveDifferentEtags() {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("preview-etags@example.com");
        String id = upload(token, png(500, 500, 0x6A5ACD), "square.png");
        waitForThumbnails(token, id);

        String preview = given().auth().oauth2(token).get(ASSETS + "/" + id + "/preview").then().extract().header("ETag");
        String thumbnail = given().auth().oauth2(token).get(ASSETS + "/" + id + "/thumbnail").then().extract().header("ETag");

        // A cache must never take one for the other.
        given().auth().oauth2(token).header("If-None-Match", thumbnail)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(200)
                .header("ETag", is(preview));
    }

    @Test
    void saysSoWhileThePreviewIsPending() {
        String token = testUsers.tokenFor("preview-pending@example.com");
        UUID id = pendingAssetOf(ownerId(token));

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(404)
                .body("error", is("thumbnail_pending"));
    }

    @Test
    void saysSoWhenTheOriginalCouldNotBeProcessed() {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("preview-failed@example.com");
        String id = upload(token, brokenJpeg(), "broken.jpg");
        waitForThumbnails(token, id);

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(404)
                .body("error", is("thumbnail_failed"));
    }

    @Test
    void aDeletedPreviewIsGeneratedAgain() throws IOException {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("preview-regen@example.com");
        String id = upload(token, png(800, 600, 0xDC143C), "regen.png");
        waitForThumbnails(token, id);

        Files.delete(derivatives.preview(ownerId(token), UUID.fromString(id)));

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(404)
                .body("error", is("thumbnail_pending"));
        assertEquals("READY", waitForThumbnails(token, id).getString("thumbnailStatus"));
        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(200);
    }

    @Test
    void anotherUsersPreviewIsNotFound() {
        String id = upload(testUsers.tokenFor("preview-owner@example.com"), png(2, 2, 0x202020), "mine.png");

        given().auth().oauth2(testUsers.tokenFor("preview-stranger@example.com"))
                .get(ASSETS + "/" + id + "/preview")
                .then().statusCode(404)
                .body("error", is("asset_not_found"));
    }

    @Test
    void requiresAuthentication() {
        given().get(ASSETS + "/" + UUID.randomUUID() + "/preview").then().statusCode(401);
    }
}