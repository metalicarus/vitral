package dev.vitral.user;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end check of the first run: setup, login, roles and sessions.
 * Runs against a real PostgreSQL started by Quarkus Dev Services (Docker required).
 */
@QuarkusTest
class AuthFlowTest {

    @Test
    void firstRunSetupLoginRolesAndLogout() {
        given().get("/api/v1/server/info")
                .then().statusCode(200).body("setupRequired", is(true));

        String adminToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", "Admin@Example.com", "displayName", "Admin", "password", "senha-forte-123"))
                .post("/api/v1/setup")
                .then().statusCode(201)
                .body("user.role", is("ADMIN"))
                .body("user.email", is("admin@example.com"))
                .extract().path("token");

        given().get("/api/v1/server/info")
                .then().statusCode(200).body("setupRequired", is(false));

        // Setup can only run once.
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "other@example.com", "displayName", "Other", "password", "senha-forte-123"))
                .post("/api/v1/setup")
                .then().statusCode(409).body("error", is("setup_done"));

        given().auth().oauth2(adminToken).get("/api/v1/users/me")
                .then().statusCode(200).body("email", is("admin@example.com"));
        given().get("/api/v1/users/me").then().statusCode(401);
        given().auth().oauth2("not-a-real-token").get("/api/v1/users/me").then().statusCode(401);

        // The admin creates a regular user.
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("email", "maria@example.com", "displayName", "Maria", "password", "outra-senha-456"))
                .post("/api/v1/admin/users")
                .then().statusCode(201).body("role", is("USER"));

        given().contentType(ContentType.JSON)
                .body(Map.of("email", "maria@example.com", "password", "senha-errada"))
                .post("/api/v1/auth/login")
                .then().statusCode(401).body("error", is("invalid_credentials"));

        String mariaToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", "maria@example.com", "password", "outra-senha-456", "deviceName", "Notebook"))
                .post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("token");

        // Regular users cannot reach admin endpoints.
        given().auth().oauth2(mariaToken).get("/api/v1/admin/users").then().statusCode(403);

        given().auth().oauth2(mariaToken).get("/api/v1/auth/sessions")
                .then().statusCode(200)
                .body("size()", is(1))
                .body("[0].current", is(true))
                .body("[0].deviceName", is("Notebook"));

        given().auth().oauth2(mariaToken).post("/api/v1/auth/logout").then().statusCode(204);
        given().auth().oauth2(mariaToken).get("/api/v1/users/me").then().statusCode(401);
    }

    @Test
    void setupRejectsWeakPasswords() {
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "admin@example.com", "displayName", "Admin", "password", "curta"))
                .post("/api/v1/setup")
                .then().statusCode(400);
    }
}
