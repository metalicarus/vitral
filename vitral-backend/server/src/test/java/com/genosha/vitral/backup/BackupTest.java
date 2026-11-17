package com.genosha.vitral.backup;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Copies of the catalog database, made on demand through /api/v1/admin/backups. */
@QuarkusTest
class BackupTest {

    private static final String BACKUPS = "/api/v1/admin/backups";

    @Inject
    TestUsers testUsers;

    @Inject
    BackupService backups;

    @Test
    void administratorsMakeListAndDownloadBackups() {
        String admin = testUsers.adminTokenFor("backup-admin@example.com");

        String name = given().auth().oauth2(admin).post(BACKUPS).then().statusCode(201).extract().path("name");

        assertTrue(BackupService.NAME.matcher(name).matches(), name);
        List<String> names = given().auth().oauth2(admin).get(BACKUPS)
                .then().statusCode(200).extract().jsonPath().getList("name");
        assertTrue(names.contains(name));
        byte[] file = given().auth().oauth2(admin).get(BACKUPS + "/" + name)
                .then().statusCode(200).extract().asByteArray();
        // Every SQLite database starts with this header.
        assertEquals("SQLite format 3", new String(Arrays.copyOf(file, 15), StandardCharsets.US_ASCII));
    }

    @Test
    void onlyTheNewestCopiesAreKept() throws InterruptedException {
        backups.create();
        // Names have a resolution of one second.
        Thread.sleep(1100);
        backups.create();

        backups.prune(1);

        assertEquals(1, backups.list().size());
    }

    @Test
    void namesOutsideTheBackupsAreRefused() {
        String admin = testUsers.adminTokenFor("backup-admin@example.com");

        given().auth().oauth2(admin).get(BACKUPS + "/..%2Fvitral.db").then().statusCode(404);
        given().auth().oauth2(admin).get(BACKUPS + "/vitral-2000-01-01T00-00-00Z.db").then().statusCode(404);
    }

    @Test
    void membersCannotSeeBackups() {
        String user = testUsers.tokenFor("backup-member@example.com");

        given().auth().oauth2(user).get(BACKUPS).then().statusCode(403);
        given().auth().oauth2(user).post(BACKUPS).then().statusCode(403);
    }
}
