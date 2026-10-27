package com.genosha.vitral.support;

import com.genosha.vitral.asset.Asset;
import com.genosha.vitral.asset.AssetType;
import com.genosha.vitral.asset.TakenAtSource;
import com.genosha.vitral.asset.ThumbnailStatus;
import com.genosha.vitral.common.Timestamps;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.path.json.JsonPath;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/** Uploads and waits for background work, for tests that go through the API. */
public final class TestAssets {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private TestAssets() {
    }

    public static String upload(String token, byte[] file, String name) {
        return given().auth().oauth2(token).multiPart("file", name, file)
                .post("/api/v1/assets").then().statusCode(201).extract().path("id");
    }

    /** Polls the asset until its thumbnails are no longer pending, and returns it. */
    public static JsonPath waitForThumbnails(String token, String id) {
        Instant deadline = Instant.now().plus(WAIT);
        while (true) {
            JsonPath asset = given().auth().oauth2(token).get("/api/v1/assets/" + id)
                    .then().statusCode(200).extract().jsonPath();
            if (!"PENDING".equals(asset.getString("thumbnailStatus"))) {
                return asset;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Thumbnails still pending after " + WAIT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /**
     * A catalog entry that is never queued, so its thumbnails stay pending whatever the
     * machine, with or without libvips. The file it points to does not exist.
     */
    public static UUID pendingAssetOf(UUID ownerId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Asset asset = new Asset();
            asset.ownerId = ownerId;
            asset.type = AssetType.PHOTO;
            asset.originalFileName = "waiting.jpg";
            asset.relativePath = ownerId + "/test/" + UUID.randomUUID() + ".jpg";
            asset.mimeType = "image/jpeg";
            asset.sizeBytes = 1;
            asset.sha256 = UUID.randomUUID().toString().replace("-", "").repeat(2);
            asset.takenAt = Timestamps.now();
            asset.takenAtSource = TakenAtSource.UPLOAD;
            asset.uploadedAt = Timestamps.now();
            asset.thumbnailStatus = ThumbnailStatus.PENDING;
            asset.persist();
            return asset.id;
        });
    }

    public static UUID ownerId(String token) {
        return UUID.fromString(given().auth().oauth2(token).get("/api/v1/users/me").then().extract().path("id"));
    }
}