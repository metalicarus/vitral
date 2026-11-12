package com.genosha.vitral.asset;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.genosha.vitral.support.TestAssets.upload;
import static com.genosha.vitral.support.TestPhotos.fixture;
import static com.genosha.vitral.support.TestPhotos.png;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Camera, exposure and GPS position: read on upload, exposed by the API, and filled in for older photos. */
@QuarkusTest
class AssetMetadataTest {

    @Inject
    TestUsers testUsers;

    @Inject
    MetadataRefresh refresh;

    @Test
    void uploadedPhotosShowHowAndWhereTheyWereTaken() {
        String token = testUsers.tokenFor("metadata-" + UUID.randomUUID() + "@example.com");
        String id = upload(token, fixture("exif-camera.jpg"), "ibirapuera.jpg");

        JsonPath asset = given().auth().oauth2(token).get("/api/v1/assets/" + id)
                .then().statusCode(200)
                .body("camera.make", is("Canon"))
                .body("camera.model", is("Canon EOS R6"))
                .body("camera.lens", is("RF24-105mm F4 L IS USM"))
                .body("camera.iso", is(400))
                .extract().jsonPath();

        assertEquals(4.0, asset.getDouble("camera.fNumber"), 0.0001);
        assertEquals(0.004, asset.getDouble("camera.exposureSeconds"), 0.00001);
        assertEquals(50.0, asset.getDouble("camera.focalLength"), 0.0001);
        assertEquals(-23.5874, asset.getDouble("location.latitude"), 0.0001);
        assertEquals(-46.6576, asset.getDouble("location.longitude"), 0.0001);
    }

    @Test
    void photosWithoutThatDataLeaveItOut() {
        String token = testUsers.tokenFor("metadata-" + UUID.randomUUID() + "@example.com");
        String id = upload(token, png(10, 10, 0x336699), "captura.png");

        given().auth().oauth2(token).get("/api/v1/assets/" + id)
                .then().statusCode(200)
                .body("camera", nullValue())
                .body("location", nullValue());
    }

    @Test
    void photosCatalogedBeforeAreReadAgain() {
        String token = testUsers.tokenFor("metadata-" + UUID.randomUUID() + "@example.com");
        UUID id = UUID.fromString(upload(token, fixture("exif-camera.jpg"), "antiga.jpg"));
        // As a row written before camera and GPS were kept.
        QuarkusTransaction.requiringNew().run(() -> {
            Asset asset = Asset.findById(id);
            asset.cameraMake = null;
            asset.cameraModel = null;
            asset.lensModel = null;
            asset.fNumber = null;
            asset.exposureSeconds = null;
            asset.iso = null;
            asset.focalLength = null;
            asset.latitude = null;
            asset.longitude = null;
            asset.metadataVersion = 0;
        });

        assertTrue(refresh.refreshAll() >= 1);

        Asset refreshed = QuarkusTransaction.requiringNew().call(() -> Asset.findById(id));
        assertEquals("Canon EOS R6", refreshed.cameraModel);
        assertEquals(-23.5874, refreshed.latitude, 0.0001);
        assertEquals(Asset.METADATA_VERSION, refreshed.metadataVersion);
        // Once current, a photo is not read again.
        assertEquals(0, refresh.refreshAll());
    }
}
