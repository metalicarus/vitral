package com.genosha.vitral.asset;

import com.genosha.vitral.ingest.FolderScanner;
import com.genosha.vitral.ingest.WatchedFolder;
import com.genosha.vitral.storage.LibraryStorage;
import com.genosha.vitral.storage.OriginalFiles;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.genosha.vitral.support.TestAssets.ownerId;
import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestPhotos.png;
import static com.genosha.vitral.support.TestPhotos.sha256;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Favorites, the archive and the trash, through the API. */
@QuarkusTest
class AssetOrganizeTest {

    private static final String ASSETS = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    AssetOrganizer organizer;

    @Inject
    LibraryStorage library;

    @Inject
    OriginalFiles originals;

    @Inject
    FolderScanner scanner;

    private String token;

    @BeforeEach
    void setUp() {
        token = testUsers.tokenFor("organize-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void favoritesAndTheArchiveHaveTheirOwnViews() {
        String praia = upload(token, png(10, 10, 0x0000AA), "praia.png");
        String print = upload(token, png(10, 10, 0x00AA00), "print.png");
        String outra = upload(token, png(10, 10, 0xAA0000), "outra.png");

        change(praia, Map.of("favorite", true)).body("favorite", is(true)).body("archived", is(false));
        change(print, Map.of("archived", true)).body("archived", is(true));

        assertEquals(List.of(outra, praia), ids("timeline"));
        assertEquals(List.of(praia), ids("favorites"));
        assertEquals(List.of(print), ids("archive"));
        assertEquals(List.of(), ids("trash"));

        change(print, Map.of("archived", false));
        assertEquals(3, ids("timeline").size());
    }

    @Test
    void theTrashKeepsPhotosUntilTheyAreRestored() {
        String id = upload(token, png(10, 10, 0x123456), "apagada.png");
        change(id, Map.of("favorite", true));

        String trashedAt = given().auth().oauth2(token).post(ASSETS + "/" + id + "/trash")
                .then().statusCode(200).body("trashedAt", notNullValue()).extract().path("trashedAt");
        assertEquals(List.of(), ids("timeline"));
        assertEquals(List.of(), ids("favorites"));
        assertEquals(List.of(id), ids("trash"));
        // Trashing again does not restart the 30 days.
        given().auth().oauth2(token).post(ASSETS + "/" + id + "/trash")
                .then().statusCode(200).body("trashedAt", is(trashedAt));

        given().auth().oauth2(token).post(ASSETS + "/" + id + "/restore")
                .then().statusCode(200).body("trashedAt", nullValue()).body("favorite", is(true));
        assertEquals(List.of(id), ids("timeline"));
    }

    @Test
    void onlyTrashedPhotosCanBeDeletedForGood() {
        String id = upload(token, png(10, 10, 0x654321), "para-sempre.png");
        Path original = libraryFile(id);
        assertTrue(Files.exists(original));

        given().auth().oauth2(token).delete(ASSETS + "/" + id)
                .then().statusCode(409).body("error", is("not_in_trash"));

        given().auth().oauth2(token).post(ASSETS + "/" + id + "/trash").then().statusCode(200);
        given().auth().oauth2(token).delete(ASSETS + "/" + id).then().statusCode(204);

        given().auth().oauth2(token).get(ASSETS + "/" + id).then().statusCode(404);
        assertFalse(Files.exists(original), "an uploaded original goes with it");
    }

    @Test
    void emptyingTheTrashDeletesEverythingInIt() {
        String a = upload(token, png(10, 10, 0x111111), "a.png");
        String b = upload(token, png(10, 10, 0x222222), "b.png");
        String kept = upload(token, png(10, 10, 0x333333), "fica.png");
        given().auth().oauth2(token).post(ASSETS + "/" + a + "/trash").then().statusCode(200);
        given().auth().oauth2(token).post(ASSETS + "/" + b + "/trash").then().statusCode(200);

        given().auth().oauth2(token).post(ASSETS + "/trash/empty").then().statusCode(200).body("deleted", is(2));

        assertEquals(List.of(kept), ids("timeline"));
        assertEquals(List.of(), ids("trash"));
    }

    @Test
    void photosTrashedLongerThanTheRetentionAreDeleted() {
        String old = upload(token, png(10, 10, 0x444444), "antiga.png");
        String recent = upload(token, png(10, 10, 0x555555), "recente.png");
        given().auth().oauth2(token).post(ASSETS + "/" + old + "/trash").then().statusCode(200);
        given().auth().oauth2(token).post(ASSETS + "/" + recent + "/trash").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            Asset asset = Asset.findById(UUID.fromString(old));
            asset.trashedAt = Instant.now().minus(Duration.ofDays(31));
        });

        assertTrue(organizer.deleteExpired() >= 1);

        assertEquals(List.of(recent), ids("trash"));
    }

    @Test
    void filesInWatchedFoldersAreNeverDeleted() throws IOException {
        Path dir = Files.createDirectories(Path.of("target/test-folders", UUID.randomUUID().toString()));
        Path file = dir.resolve("do-celular.png");
        Files.write(file, png(10, 10, 0x777777));
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2025-03-10T15:30:00Z")));
        UUID folder = watchedFolder(dir);
        scanner.scan(folder);
        String id = given().auth().oauth2(token).get(ASSETS).then().extract().path("items[0].id");

        given().auth().oauth2(token).post(ASSETS + "/" + id + "/trash").then().statusCode(200);
        given().auth().oauth2(token).delete(ASSETS + "/" + id).then().statusCode(204);

        assertTrue(Files.exists(file), "Vitral never deletes anything in a watched folder");
        // Unchanged, the file is not cataloged again by the next scan.
        scanner.scan(folder);
        assertEquals(List.of(), ids("timeline"));
    }

    @Test
    void trashedPhotosStillCountAsBackedUp() {
        byte[] photo = png(10, 10, 0x888888);
        String id = upload(token, photo, "backup.png");
        given().auth().oauth2(token).post(ASSETS + "/" + id + "/trash").then().statusCode(200);

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("hashes", List.of(sha256(photo))))
                .post(ASSETS + "/check")
                .then().statusCode(200).body("missing.size()", is(0));
    }

    @Test
    void otherPeoplesPhotosAndUnknownViewsAreRefused() {
        String id = upload(token, png(10, 10, 0x999999), "minha.png");
        String other = testUsers.tokenFor("organize-other-" + UUID.randomUUID() + "@example.com");

        given().auth().oauth2(other).post(ASSETS + "/" + id + "/trash").then().statusCode(404);
        given().auth().oauth2(other).contentType(ContentType.JSON).body(Map.of("favorite", true))
                .patch(ASSETS + "/" + id).then().statusCode(404);
        given().auth().oauth2(token).get(ASSETS + "?view=lixo").then().statusCode(400)
                .body("error", is("invalid_view"));
    }

    private io.restassured.response.ValidatableResponse change(String id, Map<String, Object> body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .patch(ASSETS + "/" + id).then().statusCode(200);
    }

    private List<String> ids(String view) {
        return given().auth().oauth2(token).get(ASSETS + "?view=" + view)
                .then().statusCode(200).extract().jsonPath().getList("items.id");
    }

    private Path libraryFile(String id) {
        String relative = QuarkusTransaction.requiringNew().call(() ->
                Asset.<Asset>findById(UUID.fromString(id)).relativePath);
        return library.resolve(relative);
    }

    private UUID watchedFolder(Path dir) throws IOException {
        Path real = dir.toRealPath();
        UUID owner = ownerId(token);
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            WatchedFolder folder = new WatchedFolder();
            folder.ownerId = owner;
            folder.path = real.toString();
            folder.createdAt = Instant.now();
            folder.persist();
            return folder.id;
        });
        originals.register(id, real);
        return id;
    }
}
