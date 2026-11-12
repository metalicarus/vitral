package com.genosha.vitral.client.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.genosha.vitral.client.api.Models.AssetPage;
import com.genosha.vitral.client.api.Models.ErrorBody;
import com.genosha.vitral.client.api.Models.LoginResult;
import com.genosha.vitral.client.api.Models.ServerInfo;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to one Vitral server over its REST API.
 *
 * <p>Every call blocks until the server answers, so call them off the JavaFX thread.
 * Failures arrive as {@link IOException}: an {@link ApiException} when the server answered
 * with an error, any other one when it could not be reached.
 */
public final class VitralApi {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper json;
    private volatile String token;

    /** @param address the server's address, such as {@code http://192.168.0.10:8080}; see {@link #normalizeAddress} */
    public VitralApi(String address) {
        this.baseUri = URI.create(normalizeAddress(address));
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // Plain HTTP/1.1: the HTTP/2 upgrade attempt only adds a round trip on a LAN.
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.json = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    /**
     * Accepts what people type: {@code localhost:8080} becomes {@code http://localhost:8080},
     * and a trailing slash is dropped.
     *
     * @throws IllegalArgumentException if the address is blank or not a valid URL
     */
    public static String normalizeAddress(String address) {
        String value = address == null ? "" : address.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("The server address is empty");
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri = URI.create(value);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Not a server address: " + address);
        }
        return value;
    }

    public URI baseUri() {
        return baseUri;
    }

    public ServerInfo serverInfo() throws IOException {
        return readJson(send(get("/api/v1/server/info")), ServerInfo.class);
    }

    /** Logs in and keeps the session token for the next calls. */
    public LoginResult login(String email, String password, String deviceName) throws IOException {
        HttpRequest request = request("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .POST(jsonBody(Map.of("email", email, "password", password, "deviceName", deviceName)))
                .build();
        LoginResult result = readJson(send(request), LoginResult.class);
        token = result.token();
        return result;
    }

    /** Ends the session on the server and forgets the token. */
    public void logout() throws IOException {
        try {
            send(request("/api/v1/auth/logout").POST(HttpRequest.BodyPublishers.noBody()).build());
        } finally {
            token = null;
        }
    }

    /**
     * One page of the user's photos, newest first.
     *
     * @param cursor the previous page's {@code nextCursor}, or null for the first page
     */
    public AssetPage listAssets(int limit, String cursor) throws IOException {
        String query = "?limit=" + limit
                + (cursor == null ? "" : "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        return readJson(send(get("/api/v1/assets" + query)), AssetPage.class);
    }

    /**
     * The photo's thumbnail, a JPEG no larger than 360 px. While it is being generated the
     * server answers with an {@link ApiException} coded {@code thumbnail_pending}.
     */
    public byte[] thumbnail(UUID assetId) throws IOException {
        return send(get("/api/v1/assets/" + assetId + "/thumbnail")).body();
    }

    /**
     * The photo's preview, a JPEG no larger than 1440 px, for showing it full screen. Fails
     * like {@link #thumbnail} while it is being generated.
     */
    public byte[] preview(UUID assetId) throws IOException {
        return send(get("/api/v1/assets/" + assetId + "/preview")).body();
    }

    /**
     * Saves the original file at {@code target}, streaming it to disk so even large files
     * never sit in memory. The bytes go to a {@code .part} file next to the target first,
     * so a failed download never leaves a broken file under the final name.
     */
    public void downloadOriginal(UUID assetId, Path target) throws IOException {
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Download> response;
        try {
            response = http.send(get("/api/v1/assets/" + assetId + "/original"), info -> info.statusCode() == 200
                    ? HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofFile(partial),
                    file -> new Download(file, null))
                    : HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(),
                    error -> new Download(null, error)));
        } catch (InterruptedException e) {
            Files.deleteIfExists(partial);
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while downloading asset " + assetId);
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        if (response.body().error() != null) {
            throw toApiException(response.statusCode(), response.body().error());
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Either the downloaded file or, when the server refused, its error body. */
    private record Download(Path file, byte[] error) {
    }

    private HttpRequest get(String path) {
        return request(path).GET().build();
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(REQUEST_TIMEOUT);
        String current = token;
        if (current != null) {
            builder.header("Authorization", "Bearer " + current);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) throws IOException {
        return HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body));
    }

    /** Sends the request and returns the response if it succeeded, or throws the server's error. */
    private HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while calling " + request.uri());
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response;
        }
        throw toApiException(status, response.body());
    }

    private ApiException toApiException(int status, byte[] body) {
        try {
            ErrorBody error = json.readValue(body, ErrorBody.class);
            if (error.error() != null) {
                return new ApiException(status, error.error(), error.message());
            }
        } catch (IOException notJson) {
            // Errors from outside Vitral's own code (a proxy, the framework) may be plain text.
        }
        return new ApiException(status, "http_" + status, "The server answered with status " + status);
    }

    private <T> T readJson(HttpResponse<byte[]> response, Class<T> type) throws IOException {
        return json.readValue(response.body(), type);
    }
}
