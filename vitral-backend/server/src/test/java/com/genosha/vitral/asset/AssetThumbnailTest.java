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

/** GET /api/v1/assets/{id}/thumbnail: the small JPEG for grids. */
@QuarkusTest
class AssetThumbnailTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    ThumbnailService thumbnails;

    @Inject
    DerivativeStorage derivatives;

    @Test
    void sendsTheThumbnailOnceItIsReady() throws IOException {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("thumb-ready@example.com");
        byte[] photo = png(2000, 1000, 0x1E90FF);
        String id = upload(token, photo, "Panorama.png");
        waitForThumbnails(token, id);
        String etag = "\"" + sha256(photo) + "-thumbnail-v" + ThumbnailService.GENERATION_VERSION + "\"";

        byte[] jpeg = given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(200)
                .contentType("image/jpeg")
                .header("ETag", is(etag))
                .header("Cache-Control", is("private, max-age=2592000"))
                .header("X-Content-Type-Options", is("nosniff"))
                .header("Content-Disposition", containsString("filename=\"Panorama-thumbnail.jpg\""))
                .extract().asByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(360, image.getWidth());
        assertEquals(180, image.getHeight());

        given().auth().oauth2(token).header("If-None-Match", etag)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(304);
    }

    @Test
    void saysSoWhileTheThumbnailIsPending() {
        String token = testUsers.tokenFor("thumb-pending@example.com");
        // A catalog entry that is never queued, so it stays pending whatever the machine.
        UUID id = pendingAssetOf(ownerId(token));

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(404)
                .body("error", is("thumbnail_pending"));
    }

    @Test
    void saysSoWhenTheOriginalCouldNotBeProcessed() {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("thumb-failed@example.com");
        String id = upload(token, brokenJpeg(), "broken.jpg");
        waitForThumbnails(token, id);

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(404)
                .body("error", is("thumbnail_failed"));
    }

    @Test
    void aDeletedThumbnailIsGeneratedAgain() throws IOException {
        assumeTrue(thumbnails.isAvailable(), "libvips is not installed: skipping");
        String token = testUsers.tokenFor("thumb-regen@example.com");
        String id = upload(token, png(800, 600, 0x00AA55), "regen.png");
        waitForThumbnails(token, id);

        Files.delete(derivatives.thumbnail(ownerId(token), UUID.fromString(id)));

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(404)
                .body("error", is("thumbnail_pending"));
        assertEquals("READY", waitForThumbnails(token, id).getString("thumbnailStatus"));
        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(200);
    }

    @Test
    void anotherUsersThumbnailIsNotFound() {
        String id = upload(testUsers.tokenFor("thumb-owner@example.com"), png(2, 2, 0x101010), "mine.png");

        given().auth().oauth2(testUsers.tokenFor("thumb-stranger@example.com"))
                .get(ASSETS + "/" + id + "/thumbnail")
                .then().statusCode(404)
                .body("error", is("asset_not_found"));
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() {
        String token = testUsers.tokenFor("thumb-unknown@example.com");
        for (String id : new String[]{UUID.randomUUID().toString(), "not-a-uuid"}) {
            given().auth().oauth2(token)
                    .get(ASSETS + "/" + id + "/thumbnail")
                    .then().statusCode(404)
                    .body("error", is("asset_not_found"));
        }
    }

    @Test
    void requiresAuthentication() {
        given().get(ASSETS + "/" + UUID.randomUUID() + "/thumbnail").then().statusCode(401);
    }
}