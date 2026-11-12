package com.genosha.vitral.client.api;

import com.genosha.vitral.client.api.Models.AssetPage;
import com.genosha.vitral.client.api.Models.LoginResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the client against a small fake server that answers like Vitral's. */
class VitralApiTest {

    private HttpServer server;
    private VitralApi api;
    /** Authorization headers received, in order. */
    private final List<String> authorizations = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        api = new VitralApi("127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void normalizesTypedAddresses() {
        assertEquals("http://localhost:8080", VitralApi.normalizeAddress(" localhost:8080/ "));
        assertEquals("https://fotos.exemplo.com", VitralApi.normalizeAddress("https://fotos.exemplo.com"));
        assertThrows(IllegalArgumentException.class, () -> VitralApi.normalizeAddress("  "));
    }

    @Test
    void readsServerInfo() throws IOException {
        respond("/api/v1/server/info", 200, """
                {"name":"Vitral","version":"0.1.0","setupRequired":true}""");

        assertTrue(api.serverInfo().setupRequired());
    }

    @Test
    void loginKeepsTheTokenForLaterCalls() throws IOException {
        respond("/api/v1/auth/login", 200, """
                {"token":"abc123","expiresAt":"2027-01-31T12:00:00Z",
                 "user":{"id":"%s","email":"ana@exemplo.com","displayName":"Ana","role":"ADMIN",
                         "createdAt":"2026-10-20T12:00:00Z"}}""".formatted(UUID.randomUUID()));
        respond("/api/v1/assets", 200, """
                {"items":[],"nextCursor":null}""");

        LoginResult result = api.login("ana@exemplo.com", "senha", "Teste");
        api.listAssets(10, null);

        assertEquals("Ana", result.user().displayName());
        assertEquals(List.of("", "Bearer abc123"), authorizations);
    }

    @Test
    void readsAPageOfAssetsAndPassesTheCursor() throws IOException {
        UUID id = UUID.randomUUID();
        List<String> queries = new ArrayList<>();
        server.createContext("/api/v1/assets", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, """
                    {"items":[{"id":"%s","type":"PHOTO","originalFileName":"IMG_1.HEIC","mimeType":"image/heic",
                               "sizeBytes":123,"sha256":"x","width":4032,"height":3024,
                               "takenAt":"2024-07-14T19:32:10Z","takenAtSource":"EXIF",
                               "uploadedAt":"2024-07-15T10:00:00Z","thumbnailStatus":"SOMETHING_NEW",
                               "camera":{"make":"Apple","model":"iPhone 15","lens":null,"fNumber":1.8,
                                         "exposureSeconds":0.008,"iso":64,"focalLength":6.86},
                               "location":{"latitude":-22.9519,"longitude":-43.2105}}],
                     "nextCursor":"djF8MTIzfGFi"}""".formatted(id));
        });

        AssetPage page = api.listAssets(50, "djF8MTIzfGFi");

        assertEquals(List.of("limit=50&cursor=djF8MTIzfGFi"), queries);
        assertEquals(id, page.items().getFirst().id());
        assertEquals("djF8MTIzfGFi", page.nextCursor());
        // A status this client does not know yet must not break the whole page.
        assertNull(page.items().getFirst().thumbnailStatus());
        assertEquals("iPhone 15", page.items().getFirst().camera().model());
        assertEquals(64, page.items().getFirst().camera().iso());
        assertEquals(-22.9519, page.items().getFirst().location().latitude(), 0.00001);
    }

    @Test
    void turnsErrorBodiesIntoApiExceptions() {
        respond("/api/v1/auth/login", 401, """
                {"error":"invalid_credentials","message":"E-mail or password is incorrect."}""");

        ApiException error = assertThrows(ApiException.class, () -> api.login("a@b.c", "x", "Teste"));

        assertEquals("invalid_credentials", error.code());
        assertTrue(error.isUnauthorized());
    }

    @Test
    void errorsWithoutAJsonBodyStillCarryTheStatus() {
        respond("/api/v1/server/info", 502, "Bad Gateway");

        ApiException error = assertThrows(ApiException.class, () -> api.serverInfo());

        assertEquals("http_502", error.code());
        assertFalse(error.isUnauthorized());
    }

    @Test
    void downloadsThumbnails() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        server.createContext("/api/v1/assets/" + id + "/thumbnail", exchange -> {
            exchange.sendResponseHeaders(200, jpeg.length);
            exchange.getResponseBody().write(jpeg);
            exchange.close();
        });

        assertArrayEquals(jpeg, api.thumbnail(id));
    }

    @Test
    void previewsWhileStillPendingFailWithTheServerCode() {
        UUID id = UUID.randomUUID();
        respond("/api/v1/assets/" + id + "/preview", 404, """
                {"error":"thumbnail_pending","message":"Still being generated."}""");

        ApiException error = assertThrows(ApiException.class, () -> api.preview(id));

        assertEquals("thumbnail_pending", error.code());
    }

    @Test
    void savesTheOriginalUnderTheChosenName(@TempDir Path folder) throws IOException {
        UUID id = UUID.randomUUID();
        byte[] original = "not really a HEIC, but bytes all the same".getBytes(StandardCharsets.UTF_8);
        server.createContext("/api/v1/assets/" + id + "/original", exchange -> {
            exchange.sendResponseHeaders(200, original.length);
            exchange.getResponseBody().write(original);
            exchange.close();
        });
        Path target = folder.resolve("IMG_1234.HEIC");

        api.downloadOriginal(id, target);

        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(folder.resolve("IMG_1234.HEIC.part")));
    }

    @Test
    void aRefusedDownloadLeavesNoFileBehind(@TempDir Path folder) throws IOException {
        UUID id = UUID.randomUUID();
        respond("/api/v1/assets/" + id + "/original", 404, """
                {"error":"original_missing","message":"The original file is missing."}""");
        Path target = folder.resolve("IMG_1234.HEIC");

        ApiException error = assertThrows(ApiException.class, () -> api.downloadOriginal(id, target));

        assertEquals("original_missing", error.code());
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void checksHashes() throws IOException {
        String have = "a".repeat(64);
        String lack = "b".repeat(64);
        List<String> bodies = new ArrayList<>();
        server.createContext("/api/v1/assets/check", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, """
                    {"existing":[{"sha256":"%s","id":"%s"}],"missing":["%s"]}""".formatted(have, UUID.randomUUID(), lack));
        });

        Models.HashCheckResult result = api.check(List.of(have, lack));

        assertEquals("{\"hashes\":[\"" + have + "\",\"" + lack + "\"]}", bodies.getFirst());
        assertEquals(List.of(lack), result.missing());
        assertEquals(have, result.existing().getFirst().sha256());
    }

    @Test
    void uploadsAsMultipartWithTheFileDate(@TempDir Path folder) throws IOException {
        Path photo = folder.resolve("Férias \"2024\".jpg");
        Files.writeString(photo, "jpeg bytes");
        List<String> received = new ArrayList<>();
        server.createContext("/api/v1/assets", exchange -> {
            received.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 201, assetJson(UUID.randomUUID()));
        });

        Models.UploadResult result = api.upload(photo, Instant.parse("2024-07-14T19:32:10Z"));

        assertTrue(result.created());
        assertTrue(received.get(0).startsWith("multipart/form-data; boundary="));
        String body = received.get(1);
        assertTrue(body.contains("name=\"file\"; filename=\"Férias %222024%22.jpg\""), body);
        assertTrue(body.contains("\r\n\r\njpeg bytes\r\n"), body);
        assertTrue(body.contains("name=\"fileModifiedAt\"\r\n\r\n2024-07-14T19:32:10Z"), body);
    }

    @Test
    void anUploadTheServerAlreadyHadIsNotCreated(@TempDir Path folder) throws IOException {
        Path photo = folder.resolve("IMG.jpg");
        Files.writeString(photo, "jpeg bytes");
        server.createContext("/api/v1/assets", exchange -> write(exchange, 200, assetJson(UUID.randomUUID())));

        assertFalse(api.upload(photo, null).created());
    }

    @Test
    void setupCreatesTheAdministratorAndKeepsItsToken() throws IOException {
        List<String> bodies = new ArrayList<>();
        server.createContext("/api/v1/setup", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 201, """
                    {"token":"novo-admin","expiresAt":"2027-02-01T12:00:00Z",
                     "user":{"id":"%s","email":"ana@exemplo.com","displayName":"Ana","role":"ADMIN"}}"""
                    .formatted(UUID.randomUUID()));
        });
        respond("/api/v1/admin/users", 200, "[]");

        Models.LoginResult result = api.setup("ana@exemplo.com", "Ana", "senha-forte-123");
        api.listUsers();

        assertTrue(result.user().isAdmin());
        assertTrue(bodies.getFirst().contains("\"displayName\":\"Ana\""));
        assertEquals(List.of("Bearer novo-admin"), authorizations);
    }

    @Test
    void listsAndCreatesUsers() throws IOException {
        List<String> received = new ArrayList<>();
        server.createContext("/api/v1/admin/users", exchange -> {
            received.add(exchange.getRequestMethod() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (exchange.getRequestMethod().equals("POST")) {
                write(exchange, 201, """
                        {"id":"%s","email":"bia@exemplo.com","displayName":"Bia","role":"USER",
                         "createdAt":"2026-11-12T10:00:00Z"}""".formatted(UUID.randomUUID()));
            } else {
                write(exchange, 200, """
                        [{"id":"%s","email":"ana@exemplo.com","displayName":"Ana","role":"ADMIN",
                          "createdAt":"2026-10-20T12:00:00Z"}]""".formatted(UUID.randomUUID()));
            }
        });

        List<Models.User> users = api.listUsers();
        Models.User created = api.createUser("bia@exemplo.com", "Bia", "senha-da-bia", false);

        assertEquals("Ana", users.getFirst().displayName());
        assertEquals(Instant.parse("2026-10-20T12:00:00Z"), users.getFirst().createdAt());
        assertFalse(created.isAdmin());
        assertTrue(received.get(1).startsWith("POST "));
        assertTrue(received.get(1).contains("\"admin\":false"));
    }

    @Test
    void managesWatchedFolders() throws IOException {
        UUID id = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        server.createContext("/api/v1/admin/folders", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            calls.add(method + " " + path);
            String folder = """
                    {"id":"%s","path":"/fotos/maria","ownerEmail":"maria@exemplo.com",
                     "createdAt":"2026-11-12T10:00:00Z","lastScanAt":null,"lastScanError":null,"photos":0}"""
                    .formatted(id);
            switch (method + " " + path.replace(id.toString(), "ID")) {
                case "GET /api/v1/admin/folders" -> write(exchange, 200, "[" + folder + "]");
                case "POST /api/v1/admin/folders" -> write(exchange, 201, folder);
                case "POST /api/v1/admin/folders/ID/scan" -> {
                    exchange.sendResponseHeaders(202, -1);
                    exchange.close();
                }
                case "DELETE /api/v1/admin/folders/ID" -> {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                }
                default -> write(exchange, 404, "{\"error\":\"folder_not_found\",\"message\":\"Not found.\"}");
            }
        });

        Models.Folder added = api.addFolder("/fotos/maria", "maria@exemplo.com");
        List<Models.Folder> folders = api.listFolders();
        api.scanFolder(id);
        api.removeFolder(id);

        assertEquals(id, added.id());
        assertEquals("/fotos/maria", folders.getFirst().path());
        assertEquals(null, folders.getFirst().lastScanAt());
        assertEquals(List.of("POST /api/v1/admin/folders", "GET /api/v1/admin/folders",
                "POST /api/v1/admin/folders/" + id + "/scan", "DELETE /api/v1/admin/folders/" + id), calls);
    }

    private static String assetJson(UUID id) {
        return """
                {"id":"%s","type":"PHOTO","originalFileName":"IMG.jpg","mimeType":"image/jpeg","sizeBytes":10,
                 "takenAt":"2024-07-14T19:32:10Z","takenAtSource":"FILE","uploadedAt":"2026-11-11T10:00:00Z",
                 "thumbnailStatus":"PENDING"}""".formatted(id);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization == null ? "" : authorization);
            write(exchange, status, body);
        });
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
