package com.genosha.vitral.ingest;

import com.genosha.vitral.asset.Asset;
import com.genosha.vitral.asset.TakenAtSource;
import com.genosha.vitral.ingest.FolderScanner.ScanResult;
import com.genosha.vitral.storage.OriginalFiles;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static com.genosha.vitral.support.TestAssets.ownerId;
import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestPhotos.fixture;
import static com.genosha.vitral.support.TestPhotos.png;
import static com.genosha.vitral.support.TestPhotos.sha256;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scans of watched folders, run directly so each test controls exactly when they happen. */
@QuarkusTest
class FolderScanTest {

    private static final Instant LAST_YEAR = Instant.parse("2025-03-10T15:30:00Z");

    @Inject
    TestUsers testUsers;

    @Inject
    FolderScanner scanner;

    @Inject
    OriginalFiles originals;

    private Path dir;
    private String token;
    private UUID ownerId;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createDirectories(Path.of("target/test-folders", UUID.randomUUID().toString()));
        token = testUsers.tokenFor("scan-" + UUID.randomUUID() + "@example.com");
        ownerId = ownerId(token);
    }

    @Test
    void catalogsPhotosWhereTheyAreWithoutTouchingTheFolder() throws IOException {
        byte[] jpeg = fixture("exif-sample.jpg");
        write("viagem/praia.jpg", jpeg);
        write("sol.png", png(30, 20, 0xFFCC00));
        write("notas.txt", "not a photo".getBytes(StandardCharsets.UTF_8));
        write(".thumbnails/cache.png", png(4, 4, 0x000000));
        write("viagem/.syncthing.praia2.jpg.tmp", jpeg);
        Map<String, String> before = snapshot();
        UUID folder = folder();

        ScanResult result = scanner.scan(folder);

        assertEquals(2, result.added());
        assertEquals(2, result.seen());
        assertEquals(before, snapshot(), "the scan must not create, change or delete anything in the folder");
        List<Asset> assets = assetsOf(folder);
        assertEquals(List.of("sol.png", "viagem/praia.jpg"), assets.stream().map(a -> a.relativePath).sorted().toList());
        Asset praia = assets.stream().filter(a -> a.relativePath.equals("viagem/praia.jpg")).findFirst().orElseThrow();
        assertEquals(TakenAtSource.EXIF, praia.takenAtSource);
        assertEquals("praia.jpg", praia.originalFileName);
        Asset sol = assets.stream().filter(a -> a.relativePath.equals("sol.png")).findFirst().orElseThrow();
        // Without EXIF, the file's modification time is the photo's date.
        assertEquals(TakenAtSource.FILE, sol.takenAtSource);
        assertEquals(LAST_YEAR, sol.takenAt);

        byte[] downloaded = given().auth().oauth2(token).get("/api/v1/assets/" + praia.id + "/original")
                .then().statusCode(200).extract().asByteArray();
        assertArrayEquals(jpeg, downloaded);
    }

    @Test
    void scanningAgainReadsNothingUnchanged() throws IOException {
        write("a.png", png(10, 10, 0x112233));
        write("b.png", png(10, 10, 0x445566));
        UUID folder = folder();
        scanner.scan(folder);

        ScanResult again = scanner.scan(folder);

        assertEquals(2, again.seen());
        assertEquals(new ScanResult(2, 0, 0, 0, 0, 0, 0, 0, true), again);
    }

    @Test
    void photosAlreadyUploadedAreLinkedNotDuplicated() throws IOException {
        byte[] photo = png(12, 12, 0x778899);
        String uploadedId = upload(token, photo, "enviada.png");
        write("copia-da-enviada.png", photo);
        UUID folder = folder();

        ScanResult result = scanner.scan(folder);

        assertEquals(1, result.linked());
        assertEquals(0, result.added());
        assertTrue(assetsOf(folder).isEmpty(), "the uploaded asset stays in the library");
        FolderFile entry = QuarkusTransaction.requiringNew().call(() ->
                FolderFile.findById(new FolderFile.Key(folder, "copia-da-enviada.png")));
        assertEquals(UUID.fromString(uploadedId), entry.assetId);
    }

    @Test
    void aRenamedFileKeepsItsAsset() throws IOException {
        write("IMG_0001.png", png(10, 10, 0xAA0000));
        UUID folder = folder();
        scanner.scan(folder);
        UUID assetId = assetsOf(folder).getFirst().id;

        Files.move(dir.resolve("IMG_0001.png"), Files.createDirectories(dir.resolve("2025")).resolve("praia.png"));
        ScanResult result = scanner.scan(folder);

        assertEquals(1, result.moved());
        Asset asset = assetsOf(folder).getFirst();
        assertEquals(assetId, asset.id);
        assertEquals("2025/praia.png", asset.relativePath);
        assertEquals("praia.png", asset.originalFileName);
    }

    @Test
    void aFileChangedInPlaceUpdatesItsAsset() throws IOException {
        write("editada.png", png(10, 10, 0x00AA00));
        UUID folder = folder();
        scanner.scan(folder);
        UUID assetId = assetsOf(folder).getFirst().id;

        byte[] edited = png(20, 10, 0x00BB00);
        write("editada.png", edited, LAST_YEAR.plusSeconds(60));
        ScanResult result = scanner.scan(folder);

        assertEquals(1, result.updated());
        Asset asset = assetsOf(folder).getFirst();
        assertEquals(assetId, asset.id);
        assertEquals(sha256(edited), asset.sha256);
        assertEquals(20, asset.width);
    }

    @Test
    void aDeletedFileStaysInTheCatalog() throws IOException {
        write("apagada.png", png(10, 10, 0x0000AA));
        UUID folder = folder();
        scanner.scan(folder);
        UUID assetId = assetsOf(folder).getFirst().id;

        Files.delete(dir.resolve("apagada.png"));
        scanner.scan(folder);

        assertEquals(List.of(assetId), assetsOf(folder).stream().map(a -> a.id).toList());
        given().auth().oauth2(token).get("/api/v1/assets/" + assetId + "/original")
                .then().statusCode(404).body("error", is("original_missing"));
    }

    @Test
    void filesStillBeingWrittenWaitForALaterScan() throws IOException {
        write("chegando.png", png(10, 10, 0x123456), Instant.now());
        UUID folder = folder();

        ScanResult early = scanner.scan(folder);
        assertEquals(0, early.added());
        assertEquals(1, early.deferred());

        Files.setLastModifiedTime(dir.resolve("chegando.png"), FileTime.from(LAST_YEAR));
        assertEquals(1, scanner.scan(folder).added());
    }

    @Test
    void filesThatAreNotPhotosAreReadOnce() throws IOException {
        write("estragada.jpg", "this is not a JPEG".getBytes(StandardCharsets.UTF_8));
        UUID folder = folder();

        assertEquals(1, scanner.scan(folder).unsupported());
        assertEquals(0, scanner.scan(folder).unsupported());
        assertTrue(assetsOf(folder).isEmpty());
    }

    @Test
    void aMissingFolderIsReportedAndItsPhotosKept() throws IOException {
        write("a.png", png(10, 10, 0x654321));
        UUID folder = folder();
        scanner.scan(folder);

        // As when the disk holding the folder is not mounted.
        Files.move(dir, dir.resolveSibling(dir.getFileName() + "-unmounted"));
        ScanResult result = scanner.scan(folder);

        assertFalse(result.ran());
        assertEquals(1, assetsOf(folder).size());
        WatchedFolder entity = QuarkusTransaction.requiringNew().call(() -> WatchedFolder.findById(folder));
        assertNotNull(entity.lastScanError);
    }

    /** A watched folder for the test's user, created directly so no background scan runs. */
    private UUID folder() throws IOException {
        Path real = dir.toRealPath();
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            WatchedFolder folder = new WatchedFolder();
            folder.ownerId = ownerId;
            folder.path = real.toString();
            folder.createdAt = Instant.now();
            folder.persist();
            return folder.id;
        });
        originals.register(id, real);
        return id;
    }

    private List<Asset> assetsOf(UUID folder) {
        return QuarkusTransaction.requiringNew().call(() -> Asset.<Asset>list("folderId", folder));
    }

    private void write(String relative, byte[] content) throws IOException {
        write(relative, content, LAST_YEAR);
    }

    private void write(String relative, byte[] content, Instant modifiedAt) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        Files.setLastModifiedTime(file, FileTime.from(modifiedAt));
    }

    /** Every file in the folder with its hash and modification time. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(dir.relativize(file).toString(),
                        sha256(Files.readAllBytes(file)) + "@" + Files.getLastModifiedTime(file));
            }
        }
        return files;
    }
}
