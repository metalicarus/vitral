package com.genosha.vitral.album;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** /api/v1/albums against a real server. */
@QuarkusTest
class AlbumTest {

    private static final String ALBUMS = "/api/v1/albums";

    @Inject
    TestUsers testUsers;

    private String token;

    @BeforeEach
    void setUp() {
        token = testUsers.tokenFor("albums-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void createFillAndBrowseAnAlbum() {
        String praia = upload(token, png(10, 10, 0x0011AA), "praia.png");
        String sol = upload(token, png(10, 10, 0xFFAA00), "sol.png");
        upload(token, png(10, 10, 0x22AA22), "fora-do-album.png");

        String album = create("  Férias 2025  ").body("name", is("Férias 2025")).body("photos", is(0))
                .body("coverAssetId", nullValue()).extract().path("id");

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("assetIds", List.of(praia, sol, praia)))
                .post(ALBUMS + "/" + album + "/assets")
                .then().statusCode(200).body("changed", is(2));
        // Adding again changes nothing.
        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("assetIds", List.of(sol)))
                .post(ALBUMS + "/" + album + "/assets")
                .then().statusCode(200).body("changed", is(0));

        assertEquals(2, photoIds(album).size());
        JsonPath view = given().auth().oauth2(token).get(ALBUMS + "/" + album)
                .then().statusCode(200).body("photos", is(2)).extract().jsonPath();
        assertEquals(true, List.of(praia, sol).contains(view.getString("coverAssetId")));
    }

    @Test
    void theCoverCanBeChosenAndAlbumsRenamed() {
        String a = upload(token, png(10, 10, 0x101010), "a.png");
        String b = upload(token, png(10, 10, 0x202020), "b.png");
        String outside = upload(token, png(10, 10, 0x303030), "c.png");
        String album = create("Casa").extract().path("id");
        add(album, a, b);

        change(album, Map.of("coverAssetId", a)).statusCode(200).body("coverAssetId", is(a));
        change(album, Map.of("name", "Casa nova")).statusCode(200).body("name", is("Casa nova"))
                .body("coverAssetId", is(a));
        change(album, Map.of("coverAssetId", outside)).statusCode(400).body("error", is("cover_not_in_album"));
        change(album, Map.of("name", "   ")).statusCode(400).body("error", is("invalid_album_name"));
    }

    @Test
    void removingPhotosAndDeletingAlbumsKeepsThePhotos() {
        String a = upload(token, png(10, 10, 0x404040), "a.png");
        String b = upload(token, png(10, 10, 0x505050), "b.png");
        String album = create("Temporário").extract().path("id");
        add(album, a, b);

        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("assetIds", List.of(a)))
                .post(ALBUMS + "/" + album + "/assets/remove")
                .then().statusCode(200).body("changed", is(1));
        assertEquals(List.of(b), photoIds(album));

        given().auth().oauth2(token).delete(ALBUMS + "/" + album).then().statusCode(204);
        given().auth().oauth2(token).get(ALBUMS + "/" + album).then().statusCode(404);
        given().auth().oauth2(token).get("/api/v1/assets/" + a).then().statusCode(200);
        given().auth().oauth2(token).get("/api/v1/assets/" + b).then().statusCode(200);
    }

    @Test
    void photosInTheTrashAreHiddenUntilRestoredAndLeaveForGoodWhenDeleted() {
        String a = upload(token, png(10, 10, 0x606060), "a.png");
        String b = upload(token, png(10, 10, 0x707070), "b.png");
        String album = create("Lixeira").extract().path("id");
        add(album, a, b);

        given().auth().oauth2(token).post("/api/v1/assets/" + a + "/trash").then().statusCode(200);
        assertEquals(List.of(b), photoIds(album));
        given().auth().oauth2(token).get(ALBUMS + "/" + album).then().body("photos", is(1));

        given().auth().oauth2(token).post("/api/v1/assets/" + a + "/restore").then().statusCode(200);
        assertEquals(2, photoIds(album).size());

        given().auth().oauth2(token).post("/api/v1/assets/" + a + "/trash").then().statusCode(200);
        given().auth().oauth2(token).delete("/api/v1/assets/" + a).then().statusCode(204);
        given().auth().oauth2(token).post("/api/v1/assets/" + a + "/restore").then().statusCode(404);
        assertEquals(List.of(b), photoIds(album));
    }

    @Test
    void albumsCanSayWhetherTheyHaveAPhoto() {
        String a = upload(token, png(10, 10, 0x808080), "a.png");
        String with = create("Com").extract().path("id");
        String without = create("Sem").extract().path("id");
        add(with, a);

        JsonPath list = given().auth().oauth2(token).get(ALBUMS + "?containing=" + a)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(true, list.getBoolean("find { it.id == '" + with + "' }.contains"));
        assertEquals(false, list.getBoolean("find { it.id == '" + without + "' }.contains"));
        // Without the parameter, the field is left out.
        given().auth().oauth2(token).get(ALBUMS).then().body("[0].contains", nullValue());
    }

    @Test
    void albumsArePrivateToTheirOwner() {
        String mine = upload(token, png(10, 10, 0x909090), "minha.png");
        String album = create("Minha").extract().path("id");
        String other = testUsers.tokenFor("albums-other-" + UUID.randomUUID() + "@example.com");
        String theirs = upload(other, png(10, 10, 0xA0A0A0), "dela.png");

        given().auth().oauth2(other).get(ALBUMS + "/" + album).then().statusCode(404);
        given().auth().oauth2(other).get(ALBUMS + "/" + album + "/assets").then().statusCode(404);
        given().auth().oauth2(other).contentType(ContentType.JSON).body(Map.of("assetIds", List.of(theirs)))
                .post(ALBUMS + "/" + album + "/assets").then().statusCode(404);
        // Someone else's photo is skipped, not added.
        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("assetIds", List.of(mine, theirs)))
                .post(ALBUMS + "/" + album + "/assets").then().statusCode(200).body("changed", is(1));
        given().auth().oauth2(other).get(ALBUMS).then().statusCode(200).body("size()", is(0));
    }

    private io.restassured.response.ValidatableResponse create(String name) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("name", name))
                .post(ALBUMS).then().statusCode(201);
    }

    private void add(String album, String... assetIds) {
        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("assetIds", List.of(assetIds)))
                .post(ALBUMS + "/" + album + "/assets").then().statusCode(200);
    }

    private io.restassured.response.ValidatableResponse change(String album, Map<String, Object> body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .patch(ALBUMS + "/" + album).then();
    }

    private List<String> photoIds(String album) {
        return given().auth().oauth2(token).get(ALBUMS + "/" + album + "/assets")
                .then().statusCode(200).extract().jsonPath().getList("items.id");
    }
}
