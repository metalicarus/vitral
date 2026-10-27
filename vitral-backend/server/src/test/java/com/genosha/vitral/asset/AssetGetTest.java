package com.genosha.vitral.asset;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GET /api/v1/assets/{id}: one asset, only for its owner. */
@QuarkusTest
class AssetGetTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Test
    void theUploadLocationLeadsToTheSamePhoto() {
        String token = testUsers.tokenFor("get-location@example.com");
        // A file date with microseconds: the database keeps milliseconds, and both
        // responses must agree on that.
        ExtractableResponse<Response> upload = upload(token, png(3, 2, 0x445566), "2024-07-14T19:32:10.123456Z");
        String location = upload.header("Location");
        Map<String, Object> uploaded = upload.jsonPath().getMap("$");

        assertTrue(location.endsWith(ASSETS + "/" + uploaded.get("id")));
        Map<String, Object> fetched = given().auth().oauth2(token)
                .get(location)
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");

        // Thumbnails are generated in the background, so that one field may have moved on.
        uploaded.remove("thumbnailStatus");
        fetched.remove("thumbnailStatus");
        assertEquals(uploaded, fetched);
        assertEquals("2024-07-14T19:32:10.123Z", fetched.get("takenAt"));
    }

    @Test
    void acceptsTheIdInUppercase() {
        String token = testUsers.tokenFor("get-upper@example.com");
        String id = upload(token, png(2, 2, 0x778899), null).path("id");

        given().auth().oauth2(token)
                .get(ASSETS + "/" + id.toUpperCase(Locale.ROOT))
                .then().statusCode(200)
                .body("id", is(id));
    }

    @Test
    void anotherUsersPhotoIsNotFound() {
        String id = upload(testUsers.tokenFor("get-owner@example.com"), png(2, 2, 0x13579B), null).path("id");

        given().auth().oauth2(testUsers.tokenFor("get-stranger@example.com"))
                .get(ASSETS + "/" + id)
                .then().statusCode(404)
                .body("error", is("asset_not_found"));
    }

    @Test
    void unknownAndMalformedIdsAreNotFound() {
        String token = testUsers.tokenFor("get-unknown@example.com");
        for (String id : new String[]{UUID.randomUUID().toString(), "not-a-uuid", "123"}) {
            given().auth().oauth2(token)
                    .get(ASSETS + "/" + id)
                    .then().statusCode(404)
                    .body("error", is("asset_not_found"));
        }
    }

    @Test
    void requiresAuthentication() {
        given().get(ASSETS + "/" + UUID.randomUUID()).then().statusCode(401);
    }

    private static ExtractableResponse<Response> upload(String token, byte[] photo, String fileModifiedAt) {
        var request = given().auth().oauth2(token).multiPart("file", "photo.png", photo, "image/png");
        if (fileModifiedAt != null) {
            request = request.multiPart("fileModifiedAt", fileModifiedAt);
        }
        return request.post(ASSETS).then().statusCode(201).extract();
    }
}