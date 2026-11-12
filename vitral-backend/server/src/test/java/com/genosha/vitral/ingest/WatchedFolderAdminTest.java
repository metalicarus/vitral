package com.genosha.vitral.ingest;

import com.genosha.vitral.asset.ThumbnailService;
import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
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
import java.util.function.Predicate;

import static com.genosha.vitral.support.TestAssets.waitForThumbnails;
import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /api/v1/admin/folders, and the background scans it starts, against a real server. */
@QuarkusTest
class WatchedFolderAdminTest {

    private static final String FOLDERS = "/api/v1/admin/folders";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Inject
    TestUsers testUsers;

    @Inject
    VitralConfig config;

    @Inject
    ThumbnailService thumbnails;

    private String admin;
    private String userEmail;
    private String user;
    private Path dir;

    @BeforeEach
    void setUp() throws IOException {
        admin = testUsers.adminTokenFor("folders-admin@example.com");
        userEmail = "folders-" + UUID.randomUUID() + "@example.com";
        user = testUsers.tokenFor(userEmail);
        dir = Files.createDirectories(Path.of("target/test-folders", UUID.randomUUID().toString()));
    }

    @Test
    void addScanInTheBackgroundAndRemove() throws IOException {
        write("2025/praia.png", png(40, 30, 0x3366CC));
        write("2025/sol.png", png(30, 40, 0xFFAA00));

        JsonPath added = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("path", dir.toAbsolutePath().toString(), "ownerEmail", userEmail))
                .post(FOLDERS)
                .then().statusCode(201)
                .body("path", is(dir.toRealPath().toString()))
                .body("ownerEmail", is(userEmail))
                .extract().jsonPath();
        String id = added.getString("id");

        JsonPath scanned = waitForFolder(id, folder -> folder.getInt("photos") == 2);
        assertEquals(null, scanned.getString("lastScanError"));
        List<String> names = given().auth().oauth2(user).get("/api/v1/assets")
                .then().statusCode(200).extract().jsonPath().getList("items.originalFileName");
        assertEquals(List.of("praia.png", "sol.png"), names.stream().sorted().toList());

        if (thumbnails.isAvailable()) {
            String assetId = given().auth().oauth2(user).get("/api/v1/assets")
                    .then().extract().jsonPath().getString("items[0].id");
            assertEquals("READY", waitForThumbnails(user, assetId).getString("thumbnailStatus"));
        }

        given().auth().oauth2(admin).delete(FOLDERS + "/" + id).then().statusCode(204);
        given().auth().oauth2(user).get("/api/v1/assets").then().statusCode(200).body("items.size()", is(0));
        assertTrue(Files.exists(dir.resolve("2025/praia.png")), "removing a folder never touches its files");
        given().auth().oauth2(admin).get(FOLDERS + "/" + id).then().statusCode(404);
    }

    @Test
    void newPhotosAreNoticedWithoutAskingForAScan() throws IOException {
        String id = add(dir).getString("id");
        waitForFolder(id, folder -> folder.getString("lastScanAt") != null);

        // As a sync tool does: written under a temporary hidden name, then renamed.
        Path temporary = dir.resolve(".syncthing.nova.png.tmp");
        Files.write(temporary, png(16, 16, 0x22AA44));
        Files.move(temporary, dir.resolve("nova.png"));
        Files.setLastModifiedTime(dir.resolve("nova.png"), FileTime.from(Instant.now().minusSeconds(60)));

        waitForFolder(id, folder -> folder.getInt("photos") == 1);
        given().auth().oauth2(user).get("/api/v1/assets")
                .then().statusCode(200).body("items[0].originalFileName", is("nova.png"));
    }

    @Test
    void photosCopiedJustBeforeTheScanAreCatalogedOnceTheySettle() throws IOException {
        // Modified now, like a file just copied: the first scan leaves it for a later one.
        Files.write(dir.resolve("recem-copiada.png"), png(16, 16, 0x8844AA));

        String id = add(dir).getString("id");

        waitForFolder(id, folder -> folder.getInt("photos") == 1);
    }

    @Test
    void onlyAdministratorsManageFolders() {
        given().auth().oauth2(user).get(FOLDERS).then().statusCode(403);
        given().auth().oauth2(user).contentType(ContentType.JSON)
                .body(Map.of("path", dir.toAbsolutePath().toString(), "ownerEmail", userEmail))
                .post(FOLDERS).then().statusCode(403);
        given().get(FOLDERS).then().statusCode(401);
    }

    @Test
    void rejectsFoldersThatCannotBeWatched() throws IOException {
        rejected("fotos/maria", userEmail, 400, "invalid_path");
        rejected(dir.resolve("nao-existe").toAbsolutePath().toString(), userEmail, 400, "folder_not_found");
        rejected(config.dataDir().toAbsolutePath().toString(), userEmail, 400, "folder_overlaps_data");
        rejected(dir.toAbsolutePath().toString(), "ninguem@example.com", 400, "owner_not_found");

        add(dir);
        rejected(dir.toAbsolutePath().toString(), userEmail, 409, "folder_overlaps");
        Path inside = Files.createDirectories(dir.resolve("dentro"));
        rejected(inside.toAbsolutePath().toString(), userEmail, 409, "folder_overlaps");
    }

    @Test
    void unknownFoldersAreNotFound() {
        String missing = UUID.randomUUID().toString();
        given().auth().oauth2(admin).get(FOLDERS + "/" + missing).then().statusCode(404);
        given().auth().oauth2(admin).post(FOLDERS + "/" + missing + "/scan").then().statusCode(404);
        given().auth().oauth2(admin).delete(FOLDERS + "/" + missing).then().statusCode(404);
        given().auth().oauth2(admin).get(FOLDERS + "/not-a-uuid").then().statusCode(404);
    }

    private JsonPath add(Path folder) {
        return given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("path", folder.toAbsolutePath().toString(), "ownerEmail", userEmail))
                .post(FOLDERS)
                .then().statusCode(201).extract().jsonPath();
    }

    private void rejected(String path, String ownerEmail, int status, String error) {
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("path", path, "ownerEmail", ownerEmail))
                .post(FOLDERS)
                .then().statusCode(status).body("error", is(error));
    }

    /** Polls the folder until {@code done} holds; the scans run in the background. */
    private JsonPath waitForFolder(String id, Predicate<JsonPath> done) {
        Instant deadline = Instant.now().plus(WAIT);
        while (true) {
            JsonPath folder = given().auth().oauth2(admin).get(FOLDERS + "/" + id)
                    .then().statusCode(200).extract().jsonPath();
            if (done.test(folder)) {
                return folder;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Folder still not as expected after " + WAIT + ": " + folder.prettify());
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private void write(String relative, byte[] content) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2025-03-10T15:30:00Z")));
    }
}
