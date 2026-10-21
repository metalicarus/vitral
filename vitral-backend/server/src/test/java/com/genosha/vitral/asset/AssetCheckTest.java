package com.genosha.vitral.asset;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.genosha.vitral.support.TestPhotos.png;
import static com.genosha.vitral.support.TestPhotos.sha256;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/** POST /api/v1/assets/check: which files a backup client still needs to send. */
@QuarkusTest
class AssetCheckTest {

    private static final String CHECK = "/api/v1/assets/check";
    private static final String UNKNOWN = "0".repeat(64);

    @Inject
    TestUsers testUsers;

    @Test
    void separatesWhatTheUserHasFromWhatIsMissing() {
        String token = testUsers.tokenFor("check@example.com");
        byte[] photo = png(6, 6, 0xABCDEF);
        String id = upload(token, photo);

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(UNKNOWN, sha256(photo))))
                .post(CHECK)
                .then().statusCode(200)
                .body("existing.size()", is(1))
                .body("existing[0].sha256", is(sha256(photo)))
                .body("existing[0].id", is(id))
                .body("missing", contains(UNKNOWN));
    }

    @Test
    void anotherUsersPhotoIsStillMissingForMe() {
        byte[] photo = png(6, 6, 0x123456);
        upload(testUsers.tokenFor("owner@example.com"), photo);

        given().auth().oauth2(testUsers.tokenFor("someone-else@example.com")).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(sha256(photo))))
                .post(CHECK)
                .then().statusCode(200)
                .body("existing", empty())
                .body("missing", contains(sha256(photo)));
    }

    @Test
    void acceptsUppercaseAndCollapsesRepeats() {
        String hash = sha256(png(2, 2, 0x010203));

        given().auth().oauth2(testUsers.tokenFor("case@example.com")).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(hash.toUpperCase(Locale.ROOT), hash, " " + hash + " ")))
                .post(CHECK)
                .then().statusCode(200)
                .body("missing", contains(hash));
    }

    @Test
    void anEmptyListIsFine() {
        given().auth().oauth2(testUsers.tokenFor("empty@example.com")).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of()))
                .post(CHECK)
                .then().statusCode(200)
                .body("existing", empty())
                .body("missing", empty());
    }

    @Test
    void rejectsMalformedHashes() {
        given().auth().oauth2(testUsers.tokenFor("malformed@example.com")).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(UNKNOWN, "not-a-hash")))
                .post(CHECK)
                .then().statusCode(400)
                .body("error", is("invalid_hash"));
    }

    @Test
    void requiresTheHashesField() {
        given().auth().oauth2(testUsers.tokenFor("nohashes@example.com")).contentType(ContentType.JSON)
                .body(Map.of())
                .post(CHECK)
                .then().statusCode(400)
                .body("error", is("missing_hashes"));
    }

    @Test
    void limitsTheNumberOfHashes() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= AssetService.MAX_HASHES_PER_CHECK; i++) {
            tooMany.add("%064x".formatted(i));
        }

        given().auth().oauth2(testUsers.tokenFor("toomany@example.com")).contentType(ContentType.JSON)
                .body(Map.of("hashes", tooMany))
                .post(CHECK)
                .then().statusCode(400)
                .body("error", is("too_many_hashes"));
    }

    @Test
    void requiresAuthentication() {
        given().contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(UNKNOWN)))
                .post(CHECK)
                .then().statusCode(401);
    }

    private static String upload(String token, byte[] photo) {
        return given().auth().oauth2(token)
                .multiPart("file", "photo.png", photo, "image/png")
                .post("/api/v1/assets")
                .then().statusCode(201)
                .extract().path("id");
    }
}