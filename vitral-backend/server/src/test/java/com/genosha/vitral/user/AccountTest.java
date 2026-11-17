package com.genosha.vitral.user;

import com.genosha.vitral.support.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/** The user's own account (/api/v1/users/me) and what administrators can do to accounts. */
@QuarkusTest
class AccountTest {

    /** The password TestUsers gives every account it creates. */
    private static final String TEST_PASSWORD = "senha-de-teste-123";

    @Inject
    TestUsers testUsers;

    @Test
    void usersChangeTheirNameAndPassword() {
        String email = newEmail();
        String token = testUsers.tokenFor(email);
        String otherDevice = login(email, TEST_PASSWORD);

        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("displayName", "  Maria Clara "))
                .patch("/api/v1/users/me").then().statusCode(200).body("displayName", is("Maria Clara"));

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", TEST_PASSWORD, "newPassword", "uma-senha-nova-456"))
                .post("/api/v1/users/me/password").then().statusCode(204);

        // This session stays; the one on the other device ends.
        given().auth().oauth2(token).get("/api/v1/users/me").then().statusCode(200);
        given().auth().oauth2(otherDevice).get("/api/v1/users/me").then().statusCode(401);
        login(email, "uma-senha-nova-456");
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", TEST_PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(401);
    }

    @Test
    void aWrongCurrentPasswordIsRefusedWithoutLoggingOut() {
        String token = testUsers.tokenFor(newEmail());

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", "nao-e-essa", "newPassword", "uma-senha-nova-456"))
                .post("/api/v1/users/me/password").then().statusCode(400).body("error", is("wrong_password"));
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", TEST_PASSWORD, "newPassword", "curta"))
                .post("/api/v1/users/me/password").then().statusCode(400).body("error", is("invalid_password"));
        given().auth().oauth2(token).get("/api/v1/users/me").then().statusCode(200);
    }

    @Test
    void administratorsResetForgottenPasswords() {
        String admin = testUsers.adminTokenFor("accounts-admin@example.com");
        String email = newEmail();
        String userToken = testUsers.tokenFor(email);
        String id = given().auth().oauth2(userToken).get("/api/v1/users/me").then().extract().path("id");

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("newPassword", "senha-redefinida-789"))
                .post("/api/v1/admin/users/" + id + "/password").then().statusCode(204);

        given().auth().oauth2(userToken).get("/api/v1/users/me").then().statusCode(401);
        login(email, "senha-redefinida-789");
    }

    @Test
    void disabledAccountsCannotLogInUntilEnabledAgain() {
        String admin = testUsers.adminTokenFor("accounts-admin@example.com");
        String email = newEmail();
        String userToken = testUsers.tokenFor(email);
        String id = given().auth().oauth2(userToken).get("/api/v1/users/me").then().extract().path("id");

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("disabled", true))
                .patch("/api/v1/admin/users/" + id).then().statusCode(200).body("disabled", is(true));

        given().auth().oauth2(userToken).get("/api/v1/users/me").then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", TEST_PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(403).body("error", is("account_disabled"));

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("disabled", false))
                .patch("/api/v1/admin/users/" + id).then().statusCode(200).body("disabled", is(false));
        login(email, TEST_PASSWORD);
    }

    @Test
    void administratorsCanBePromotedAndDemoted() {
        String admin = testUsers.adminTokenFor("accounts-admin@example.com");
        String userToken = testUsers.tokenFor(newEmail());
        String id = given().auth().oauth2(userToken).get("/api/v1/users/me").then().extract().path("id");

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("admin", true))
                .patch("/api/v1/admin/users/" + id).then().statusCode(200).body("role", is("ADMIN"));
        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("admin", false))
                .patch("/api/v1/admin/users/" + id).then().statusCode(200).body("role", is("USER"));
    }

    @Test
    void administratorsCannotChangeTheirOwnAccountThisWay() {
        String admin = testUsers.adminTokenFor("accounts-admin@example.com");
        String id = given().auth().oauth2(admin).get("/api/v1/users/me").then().extract().path("id");

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("disabled", true))
                .patch("/api/v1/admin/users/" + id).then().statusCode(409).body("error", is("cannot_change_self"));
    }

    @Test
    void membersCannotManageAccounts() {
        String userToken = testUsers.tokenFor(newEmail());
        String other = testUsers.tokenFor(newEmail());
        String id = given().auth().oauth2(other).get("/api/v1/users/me").then().extract().path("id");

        given().auth().oauth2(userToken).contentType(ContentType.JSON).body(Map.of("disabled", true))
                .patch("/api/v1/admin/users/" + id).then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON).body(Map.of("newPassword", "senha-roubada-1"))
                .post("/api/v1/admin/users/" + id + "/password").then().statusCode(403);
    }

    private static String login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password, "deviceName", "Teste"))
                .post("/api/v1/auth/login").then().statusCode(200).extract().path("token");
    }

    private static String newEmail() {
        return "account-" + UUID.randomUUID() + "@example.com";
    }
}
