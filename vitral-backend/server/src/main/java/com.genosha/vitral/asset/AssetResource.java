package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;

@Path("/api/v1/assets")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AssetResource {

    @Inject
    AssetService assets;

    @Inject
    CurrentUser currentUser;

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