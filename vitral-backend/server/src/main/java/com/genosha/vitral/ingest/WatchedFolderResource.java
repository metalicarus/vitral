package com.genosha.vitral.ingest;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Watched folders, managed by administrators: folders on the server whose photos are
 * cataloged where they are, such as a folder kept in sync with a phone by Syncthing.
 */
@Path("/api/v1/admin/folders")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class WatchedFolderResource {

    @Inject
    WatchedFolderService folders;

    @GET
    public List<FolderView> list() {
        return folders.list().stream().map(this::view).toList();
    }

    @GET
    @Path("/{id}")
    public FolderView get(@PathParam("id") String id) {
        return folders.find(parseId(id)).map(this::view).orElseThrow(WatchedFolderResource::folderNotFound);
    }

    /** Starts watching a folder; its photos are cataloged in the background, starting right away. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@Valid AddFolderRequest request) {
        WatchedFolder folder = folders.add(request.path(), request.ownerEmail());
        return Response.created(URI.create("/api/v1/admin/folders/" + folder.id)).entity(view(folder)).build();
    }

    /** Scans the folder now instead of waiting for the next periodic scan. */
    @POST
    @Path("/{id}/scan")
    public Response scan(@PathParam("id") String id) {
        UUID folderId = parseId(id);
        if (folders.find(folderId).isEmpty()) {
            throw folderNotFound();
        }
        folders.scanSoon(folderId);
        return Response.accepted().build();
    }

    /**
     * Stops watching the folder and takes its photos out of the catalog. The files in the
     * folder are never touched.
     */
    @DELETE
    @Path("/{id}")
    public void remove(@PathParam("id") String id) {
        if (!folders.remove(parseId(id))) {
            throw folderNotFound();
        }
    }

    private FolderView view(WatchedFolder folder) {
        User owner = User.findById(folder.ownerId);
        return new FolderView(folder.id, folder.path, owner == null ? null : owner.email, folder.createdAt,
                folder.lastScanAt, folder.lastScanError, folders.fileCount(folder.id));
    }

    private static UUID parseId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw folderNotFound();
        }
    }

    private static ApiException folderNotFound() {
        return ApiException.notFound("folder_not_found", "Watched folder not found.");
    }
}
