package com.genosha.vitral.asset;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** GET /api/v1/assets: newest first, one page at a time, stable while uploads happen. */
@QuarkusTest
class AssetListTest {

    private static final String LIST = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    private int nextColor = 0x100000;

    @Test
    void anEmptyLibraryHasNoItemsAndNoNextPage() {
        given().auth().oauth2(testUsers.tokenFor("list-empty@example.com"))
                .get(LIST)
                .then().statusCode(200)
                .body("items", empty())
                .body("nextCursor", nullValue());
    }

    @Test
    void listsNewestFirstByCaptureDate() {
        String token = testUsers.tokenFor("list-order@example.com");
        String y2021 = upload(token, "2021-06-01T12:00:00Z");
        String y2023 = upload(token, "2023-06-01T12:00:00Z");
        String y2022 = upload(token, "2022-06-01T12:00:00Z");

        given().auth().oauth2(token)
                .get(LIST)
                .then().statusCode(200)
                .body("items.id", contains(y2023, y2022, y2021))
                .body("items[0].takenAt", is("2023-06-01T12:00:00Z"))
                .body("nextCursor", nullValue());
    }

    @Test
    void pagesThroughEverythingExactlyOnce() {
        String token = testUsers.tokenFor("list-pages@example.com");
        List<String> uploaded = new ArrayList<>();
        for (int day = 5; day >= 1; day--) {
            uploaded.add(upload(token, "2024-03-0" + day + "T10:00:00Z"));
        }

        List<List<String>> pages = readAllPages(token, 2);

        assertEquals(List.of(2, 2, 1), pages.stream().map(List::size).toList());
        assertEquals(uploaded, pages.stream().flatMap(List::stream).toList());
    }

    @Test
    void photosTakenAtTheSameMomentAreNeitherRepeatedNorSkipped() {
        String token = testUsers.tokenFor("list-ties@example.com");
        List<String> uploaded = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            uploaded.add(upload(token, "2024-08-10T15:00:00Z"));
        }

        List<String> seen = readAllPages(token, 1).stream().flatMap(List::stream).toList();

        assertEquals(4, seen.size());
        assertEquals(new HashSet<>(uploaded), new HashSet<>(seen));
    }

    @Test
    void uploadsDuringPaginationDoNotShiftThePages() {
        String token = testUsers.tokenFor("list-stable@example.com");
        String a = upload(token, "2024-01-01T00:00:00Z");
        String b = upload(token, "2023-01-01T00:00:00Z");

        JsonPath first = page(token, 1, null);
        assertEquals(List.of(a), first.getList("items.id"));

        // A newer photo belongs before the cursor, an older one after it.
        upload(token, "2025-01-01T00:00:00Z");
        String older = upload(token, "2022-01-01T00:00:00Z");

        JsonPath second = page(token, 1, first.getString("nextCursor"));
        JsonPath third = page(token, 1, second.getString("nextCursor"));

        assertEquals(List.of(b), second.getList("items.id"));
        assertEquals(List.of(older), third.getList("items.id"));
        assertNull(third.getString("nextCursor"));
    }

    @Test
    void showsOnlyTheUsersOwnPhotos() {
        upload(testUsers.tokenFor("list-other@example.com"), "2024-01-01T00:00:00Z");

        given().auth().oauth2(testUsers.tokenFor("list-mine@example.com"))
                .get(LIST)
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void rejectsInvalidLimits() {
        String token = testUsers.tokenFor("list-limits@example.com");
        for (String limit : new String[]{"0", "501", "-3", "abc", "2.5"}) {
            given().auth().oauth2(token).queryParam("limit", limit)
                    .get(LIST)
                    .then().statusCode(400)
                    .body("error", is("invalid_limit"));
        }
    }

    @Test
    void rejectsCursorsTheServerDidNotProduce() {
        given().auth().oauth2(testUsers.tokenFor("list-cursor@example.com")).queryParam("cursor", "abc123")
                .get(LIST)
                .then().statusCode(400)
                .body("error", is("invalid_cursor"));
    }

    @Test
    void requiresAuthentication() {
        given().get(LIST).then().statusCode(401);
    }

    /** Uploads a unique photo dated by its file time, and returns its id. */
    private String upload(String token, String fileModifiedAt) {
        return given().auth().oauth2(token)
                .multiPart("file", "photo.png", png(2, 2, nextColor++), "image/png")
                .multiPart("fileModifiedAt", fileModifiedAt)
                .post(LIST)
                .then().statusCode(201)
                .extract().path("id");
    }

    private static JsonPath page(String token, int limit, String cursor) {
        var request = given().auth().oauth2(token).queryParam("limit", limit);
        if (cursor != null) {
            request = request.queryParam("cursor", cursor);
        }
        return request.get(LIST).then().statusCode(200).extract().jsonPath();
    }

    private static List<List<String>> readAllPages(String token, int limit) {
        List<List<String>> pages = new ArrayList<>();
        String cursor = null;
        do {
            JsonPath page = page(token, limit, cursor);
            pages.add(page.getList("items.id"));
            cursor = page.getString("nextCursor");
        } while (cursor != null);
        return pages;
    }
}