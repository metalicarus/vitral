package com.genosha.vitral.album;

import com.genosha.vitral.asset.AssetPage;
import com.genosha.vitral.asset.AssetService;
import com.genosha.vitral.asset.PageParameters;
import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The user's albums: named collections of their photos. */
@Path("/api/v1/albums")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AlbumResource {

    @Inject
    AlbumService albums;

    @Inject
    AssetService assets;

    @Inject
    CurrentUser currentUser;

    /** Body of POST /api/v1/albums. */
    public record NewAlbum(String name) {
    }

    /** Body of PATCH /api/v1/albums/{id}; fields left out keep their value. */
    public record AlbumChanges(String name, UUID coverAssetId) {
    }

    /** Body of the requests that add photos to an album or remove them. */
    public record Photos(List<UUID> assetIds) {
    }

    /** How many photos a request actually added or removed. */
    public record PhotosChanged(int changed) {
    }

    /**
     * The user's albums, most recently changed first. With {@code containing}, each album also
     * says whether it has that photo, for an "add to album" list with marks.
     */
    @GET
    public List<AlbumView> list(@QueryParam("containing") String containing) {
        UUID assetId = containing == null || containing.isBlank() ? null : parseId(containing, "invalid_asset_id");
        return albums.list(userId(), assetId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(NewAlbum request) {
        AlbumView created = albums.create(userId(), request == null ? null : request.name());
        return Response.created(URI.create("/api/v1/albums/" + created.id())).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public AlbumView get(@PathParam("id") String id) {
        return albums.get(userId(), albumId(id));
    }

    /** Renames the album or chooses its cover, which must be one of its photos. */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public AlbumView change(@PathParam("id") String id, AlbumChanges changes) {
        if (changes == null || (changes.name() == null && changes.coverAssetId() == null)) {
            throw ApiException.badRequest("missing_changes", "Send name and/or coverAssetId.");
        }
        return albums.change(userId(), albumId(id), changes.name(), changes.coverAssetId());
    }

    /** Deletes the album; its photos stay in the library. */
    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id) {
        albums.delete(userId(), albumId(id));
    }

    /** The album's photos, newest first, one page at a time, like GET /api/v1/assets. */
    @GET
    @Path("/{id}/assets")
    public AssetPage photos(@PathParam("id") String id, @QueryParam("limit") String limit,
                            @QueryParam("cursor") String cursor) {
        UUID albumId = albumId(id);
        albums.requireOwned(userId(), albumId);
        return assets.listInAlbum(userId(), albumId, PageParameters.limit(limit), PageParameters.cursor(cursor));
    }

    @POST
    @Path("/{id}/assets")
    @Consumes(MediaType.APPLICATION_JSON)
    public PhotosChanged add(@PathParam("id") String id, Photos request) {
        return new PhotosChanged(albums.add(userId(), albumId(id), request == null ? null : request.assetIds()));
    }

    /** Takes photos out of the album (a POST, since a body on DELETE is poorly supported). */
    @POST
    @Path("/{id}/assets/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public PhotosChanged remove(@PathParam("id") String id, Photos request) {
        return new PhotosChanged(albums.remove(userId(), albumId(id), request == null ? null : request.assetIds()));
    }

    private UUID userId() {
        return currentUser.get().userId();
    }

    private static UUID albumId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("album_not_found", "Album not found.");
        }
    }

    private static UUID parseId(String value, String code) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(code, "Not a valid id: " + value);
        }
    }
}
