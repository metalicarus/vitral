package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.ContentDisposition;
import com.genosha.vitral.user.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;

@Path("/api/v1/assets")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AssetResource {

    @Inject
    AssetService assets;

    @Inject
    CurrentUser currentUser;

    /**
     * Lists the user's assets, newest first, one page at a time. Query parameters arrive as
     * strings so that bad values get a clear 400 instead of the 404 JAX-RS gives for
     * parameters it cannot convert.
     */
    @GET
    public AssetPage list(@QueryParam("limit") String limit, @QueryParam("cursor") String cursor) {
        return assets.list(currentUser.get().userId(), parseLimit(limit), parseCursor(cursor));
    }

    /**
     * One asset of the user. Responds 404 alike when it does not exist, belongs to someone
     * else or the id is not even a UUID, so nobody can probe for other people's assets.
     */
    @GET
    @Path("/{id}")
    public AssetView get(@PathParam("id") String id) {
        UUID assetId = parseId(id);
        return assets.find(currentUser.get().userId(), assetId)
                .map(AssetView::of)
                .orElseThrow(AssetResource::assetNotFound);
    }

    /**
     * Sends the original file, straight from disk. The ETag is the file's SHA-256, so a client
     * that already has the file gets a 304 without the bytes being sent again. The cache is
     * private: browsers may keep the photo, proxies along the way may not.
     */
    @GET
    @Path("/{id}/original")
    @Produces(MediaType.WILDCARD)
    public Response original(@PathParam("id") String id, @HeaderParam("If-None-Match") String ifNoneMatch) {
        Asset asset = assets.find(currentUser.get().userId(), parseId(id)).orElseThrow(AssetResource::assetNotFound);
        String etag = "\"" + asset.sha256 + "\"";
        Response.ResponseBuilder response = matchesEtag(ifNoneMatch, etag)
                ? Response.notModified()
                : Response.ok(assets.originalFile(asset), asset.mimeType)
                .header("Content-Disposition", ContentDisposition.inline(asset.originalFileName));
        return response
                .header("ETag", etag)
                .header("Cache-Control", "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    /**
     * The asset's thumbnail, a JPEG no larger than 360 px on its longest side, for grids.
     * Responds 404 thumbnail_pending while it is being generated and thumbnail_failed when
     * the original could not be processed.
     */
    @GET
    @Path("/{id}/thumbnail")
    @Produces(MediaType.WILDCARD)
    public Response thumbnail(@PathParam("id") String id, @HeaderParam("If-None-Match") String ifNoneMatch) {
        return generated(id, ifNoneMatch, "thumbnail", assets::thumbnailFile);
    }

    /**
     * The asset's preview, a JPEG no larger than 1440 px on its longest side, for showing one
     * photo full screen without downloading the original. Same responses as the thumbnail.
     */
    @GET
    @Path("/{id}/preview")
    @Produces(MediaType.WILDCARD)
    public Response preview(@PathParam("id") String id, @HeaderParam("If-None-Match") String ifNoneMatch) {
        return generated(id, ifNoneMatch, "preview", assets::previewFile);
    }

    /**
     * Serves a generated JPEG. Its ETag joins the photo's SHA-256 with the generation
     * settings' version. Cached for 30 days, since grids ask for these all the time, and
     * revalidated with the ETag after that.
     */
    private Response generated(String id, String ifNoneMatch, String kind, Function<Asset, java.nio.file.Path> file) {
        Asset asset = assets.find(currentUser.get().userId(), parseId(id)).orElseThrow(AssetResource::assetNotFound);
        String etag = "\"" + asset.sha256 + "-" + kind + "-v" + ThumbnailService.GENERATION_VERSION + "\"";
        Response.ResponseBuilder response = matchesEtag(ifNoneMatch, etag)
                ? Response.notModified()
                : Response.ok(file.apply(asset), "image/jpeg")
                .header("Content-Disposition", ContentDisposition.inline(generatedName(asset, kind)));
        return response
                .header("ETag", etag)
                .header("Cache-Control", "private, max-age=2592000")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    /** IMG_1234.HEIC becomes IMG_1234-thumbnail.jpg, the name offered when saving the image. */
    private static String generatedName(Asset asset, String kind) {
        String name = asset.originalFileName;
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + "-" + kind + ".jpg";
    }

    /** If-None-Match may list several tags, mark them as weak (W/) or be "*". */
    static boolean matchesEtag(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.strip();
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.equals("*") || tag.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uploads one photo. Responds 201 for a new photo and 200 with the existing one when
     * the user already has a file with the same content, so repeating a backup is harmless.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("file") FileUpload file, @RestForm("fileModifiedAt") String fileModifiedAt) {
        if (file == null) {
            throw ApiException.badRequest("missing_file", "Send the photo in the multipart field \"file\".");
        }
        Instant modifiedAt = parseInstant(fileModifiedAt);
        AssetService.UploadResult result = assets.upload(currentUser.get().userId(), file.uploadedFile(),
                file.fileName(), modifiedAt);
        AssetView view = AssetView.of(result.asset());
        if (!result.created()) {
            return Response.ok(view).build();
        }
        return Response.created(URI.create("/api/v1/assets/" + view.id())).entity(view).build();
    }

    /**
     * Tells a backup client which of its files the server does not have yet, so it only
     * uploads those. Accepts up to 1000 hashes per request.
     */
    @POST
    @Path("/check")
    @Consumes(MediaType.APPLICATION_JSON)
    public HashCheckResult check(HashCheckRequest request) {
        return assets.check(currentUser.get().userId(), request == null ? null : request.hashes());
    }

    /** Taken as a string: a UUID parameter would make JAX-RS answer a bare 404 for malformed ids. */
    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw assetNotFound();
        }
    }

    private static ApiException assetNotFound() {
        return ApiException.notFound("asset_not_found", "Asset not found.");
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return AssetService.DEFAULT_PAGE_SIZE;
        }
        try {
            int limit = Integer.parseInt(value.strip());
            if (limit >= 1 && limit <= AssetService.MAX_PAGE_SIZE) {
                return limit;
            }
        } catch (NumberFormatException ignored) {
            // Falls through to the error below.
        }
        throw ApiException.badRequest("invalid_limit",
                "limit must be a whole number from 1 to " + AssetService.MAX_PAGE_SIZE + ".");
    }

    private static AssetCursor parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AssetCursor.decode(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_cursor",
                    "Use the nextCursor value from the previous page, unchanged.");
        }
    }

    /** Accepts ISO-8601 with an offset, such as {@code 2024-07-14T19:32:10Z} or {@code 2024-07-14T16:32:10-03:00}. */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeException e) {
            throw ApiException.badRequest("invalid_file_modified_at",
                    "fileModifiedAt must be an ISO-8601 date-time with offset, such as 2024-07-14T19:32:10Z.");
        }
    }
}