package com.genosha.vitral.asset;

import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** POST /api/v1/assets against a real server, database and library folder. */
@QuarkusTest
class AssetUploadTest {

    private static final String UPLOAD = "/api/v1/assets";

    @Inject
    TestUsers testUsers;

    @Inject
    VitralConfig config;

    @Test
    void storesAPhotoInTheFolderOfItsExifDate() throws Exception {
        String token = testUsers.tokenFor("exif@example.com");
        byte[] jpeg = fixture("exif-sample.jpg");

        given().auth().oauth2(token)
                .multiPart("file", "ferias na praia.jpg", jpeg, "image/jpeg")
                .post(UPLOAD)
                .then().statusCode(201)
                .header("Location", containsString("/api/v1/assets/"))
                .body("id", not(nullValue()))
                .body("type", is("PHOTO"))
                .body("mimeType", is("image/jpeg"))
                .body("originalFileName", is("ferias na praia.jpg"))
                .body("sizeBytes", is(jpeg.length))
                .body("sha256", is(sha256(jpeg)))
                .body("width", is(16))
                .body("height", is(8))
                // 16:32:10 at UTC-03:00, as recorded by the camera
                .body("takenAt", is("2024-07-14T19:32:10Z"))
                .body("takenAtSource", is("EXIF"))
                .body("uploadedAt", not(nullValue()));

        Path stored = library(token).resolve("2024/07/ferias na praia.jpg");
        assertArrayEquals(jpeg, Files.readAllBytes(stored));
    }

    @Test
    void sameContentTwiceReturnsTheExistingPhoto() throws Exception {
        String token = testUsers.tokenFor("duplicate@example.com");
        byte[] jpeg = fixture("exif-sample.jpg");

        String id = given().auth().oauth2(token)
                .multiPart("file", "first.jpg", jpeg, "image/jpeg")
                .post(UPLOAD)
                .then().statusCode(201)
                .extract().path("id");

        given().auth().oauth2(token)
                .multiPart("file", "copy with another name.jpg", jpeg, "image/jpeg")
                .post(UPLOAD)
                .then().statusCode(200)
                .body("id", is(id))
                .body("originalFileName", is("first.jpg"));

        assertFalse(Files.exists(library(token).resolve("2024/07/copy with another name.jpg")));
    }

    @Test
    void duplicatesAreDetectedPerUser() throws Exception {
        byte[] jpeg = fixture("exif-sample.jpg");
        String first = given().auth().oauth2(testUsers.tokenFor("ana@example.com"))
                .multiPart("file", "shared.jpg", jpeg, "image/jpeg")
                .post(UPLOAD).then().statusCode(201).extract().path("id");

        given().auth().oauth2(testUsers.tokenFor("bia@example.com"))
                .multiPart("file", "shared.jpg", jpeg, "image/jpeg")
                .post(UPLOAD).then().statusCode(201).body("id", not(is(first)));
    }

    @Test
    void usesTheFileDateWhenThereIsNoExif() {
        String token = testUsers.tokenFor("filedate@example.com");

        // 23:30 UTC on Dec 31 is still 20:30 on Dec 31 in São Paulo (the test time zone).
        given().auth().oauth2(token)
                .multiPart("file", "scan.png", png(10, 20, 0x336699), "image/png")
                .multiPart("fileModifiedAt", "2023-12-31T23:30:00Z")
                .post(UPLOAD)
                .then().statusCode(201)
                .body("mimeType", is("image/png"))
                .body("width", is(10))
                .body("height", is(20))
                .body("takenAt", is("2023-12-31T23:30:00Z"))
                .body("takenAtSource", is("FILE"));

        assertTrue(Files.exists(library(token).resolve("2023/12/scan.png")));
    }

    @Test
    void fallsBackToTheUploadTime() {
        given().auth().oauth2(testUsers.tokenFor("nodate@example.com"))
                .multiPart("file", "no-date.png", png(4, 4, 0x112233), "image/png")
                .post(UPLOAD)
                .then().statusCode(201)
                .body("takenAtSource", is("UPLOAD"));
    }

    @Test
    void differentPhotosWithTheSameNameGetACounter() {
        String token = testUsers.tokenFor("names@example.com");
        for (int color : new int[]{0xFF0000, 0x00FF00}) {
            given().auth().oauth2(token)
                    .multiPart("file", "IMG_0001.png", png(3, 3, color), "image/png")
                    .multiPart("fileModifiedAt", "2025-05-10T12:00:00Z")
                    .post(UPLOAD)
                    .then().statusCode(201)
                    .body("originalFileName", is("IMG_0001.png"));
        }
        Path month = library(token).resolve("2025/05");
        assertTrue(Files.exists(month.resolve("IMG_0001.png")));
        assertTrue(Files.exists(month.resolve("IMG_0001 (1).png")));
    }

    @Test
    void rejectsFilesThatAreNotPhotos() {
        given().auth().oauth2(testUsers.tokenFor("text@example.com"))
                .multiPart("file", "fake.jpg", "just some text".getBytes(), "image/jpeg")
                .post(UPLOAD)
                .then().statusCode(415)
                .body("error", is("unsupported_media_type"));
    }

    @Test
    void requiresTheFileField() {
        given().auth().oauth2(testUsers.tokenFor("nofile@example.com"))
                .multiPart("fileModifiedAt", "2024-01-01T00:00:00Z")
                .post(UPLOAD)
                .then().statusCode(400)
                .body("error", is("missing_file"));
    }

    @Test
    void rejectsAnInvalidFileDate() throws Exception {
        given().auth().oauth2(testUsers.tokenFor("baddate@example.com"))
                .multiPart("file", "photo.jpg", fixture("exif-sample.jpg"), "image/jpeg")
                .multiPart("fileModifiedAt", "ontem")
                .post(UPLOAD)
                .then().statusCode(400)
                .body("error", is("invalid_file_modified_at"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        given().multiPart("file", "photo.jpg", fixture("exif-sample.jpg"), "image/jpeg")
                .post(UPLOAD)
                .then().statusCode(401);
    }

    private Path library(String token) {
        String userId = given().auth().oauth2(token).get("/api/v1/users/me").then().extract().path("id");
        return config.dataDir().resolve("library").resolve(userId);
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = AssetUploadTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IOException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        }
    }

    static byte[] png(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, rgb);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}