package com.genosha.vitral.asset;

import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.genosha.vitral.support.TestPhotos.fixture;
import static com.genosha.vitral.support.TestPhotos.png;
import static com.genosha.vitral.support.TestPhotos.sha256;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** GET /api/v1/assets/{id}/original: the file itself, for its owner only. */
@QuarkusTest
class AssetOriginalTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    VitralConfig config;

    @Test
    void sendsTheExactBytesWithTheRightHeaders() {
        String token = testUsers.tokenFor("original@example.com");
        byte[] jpeg = fixture("exif-sample.jpg");
        String id = upload(token, jpeg, "foto da praia.jpg", null);

        byte[] downloaded = given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(200)
                .contentType("image/jpeg")
                .header("ETag", is("\"" + sha256(jpeg) + "\""))
                .header("Content-Disposition", containsString("filename=\"foto da praia.jpg\""))
                .header("Cache-Control", is("private, max-age=86400"))
                .header("X-Content-Type-Options", is("nosniff"))
                .extract().asByteArray();

        assertArrayEquals(jpeg, downloaded);
    }

    @Test
    void aClientThatAlreadyHasTheFileGetsA304() {
        String token = testUsers.tokenFor("original-cache@example.com");
        byte[] photo = png(4, 4, 0x2468AC);
        String id = upload(token, photo, "cached.png", null);
        String etag = "\"" + sha256(photo) + "\"";

        given().auth().oauth2(token).header("If-None-Match", etag)
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(304)
                .header("ETag", is(etag))
                .body(emptyString());

        given().auth().oauth2(token).header("If-None-Match", "\"something-else\", W/" + etag)
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(304);

        given().auth().oauth2(token).header("If-None-Match", "\"" + "0".repeat(64) + "\"")
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(200);
    }

    @Test
    void anotherUsersFileIsNotFound() {
        String id = upload(testUsers.tokenFor("original-owner@example.com"), png(2, 2, 0x0F0F0F), "mine.png", null);

        given().auth().oauth2(testUsers.tokenFor("original-stranger@example.com"))
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(404)
                .body("error", is("asset_not_found"));
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() {
        String token = testUsers.tokenFor("original-unknown@example.com");
        for (String id : new String[]{UUID.randomUUID().toString(), "not-a-uuid"}) {
            given().auth().oauth2(token)
                    .get(ASSETS + "/" + id + "/original")
                    .then().statusCode(404)
                    .body("error", is("asset_not_found"));
        }
    }

    @Test
    void saysSoWhenTheFileIsGoneFromTheDisk() throws IOException {
        String token = testUsers.tokenFor("original-missing@example.com");
        String id = upload(token, png(2, 2, 0x5A5A5A), "vanished.png", "2025-02-10T12:00:00Z");
        String userId = given().auth().oauth2(token).get("/api/v1/users/me").then().extract().path("id");
        Path file = config.dataDir().resolve("library").resolve(userId).resolve("2025/02/vanished.png");

        Files.delete(file);

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id + "/original")
                .then().statusCode(404)
                .body("error", is("original_missing"));
    }

    @Test
    void requiresAuthentication() {
        given().get(ASSETS + "/" + UUID.randomUUID() + "/original").then().statusCode(401);
    }

    private static String upload(String token, byte[] photo, String name, String fileModifiedAt) {
        var request = given().auth().oauth2(token).multiPart("file", name, photo);
        if (fileModifiedAt != null) {
            request = request.multiPart("fileModifiedAt", fileModifiedAt);
        }
        return request.post(ASSETS).then().statusCode(201).extract().path("id");
    }
}