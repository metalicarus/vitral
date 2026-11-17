package com.genosha.vitral.backup;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.ContentDisposition;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Copies of the catalog database, for administrators: list them, make one now, or download
 * one to keep it somewhere else. Restoring is done by hand, with the server stopped (see the README).
 */
@Path("/api/v1/admin/backups")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class BackupResource {

    @Inject
    BackupService backups;

    /** The copies, newest first. */
    @GET
    public List<BackupService.Backup> list() {
        return backups.list();
    }

    @POST
    public Response create() {
        return Response.status(Response.Status.CREATED).entity(backups.create()).build();
    }

    /** Downloads one copy: an ordinary SQLite file. */
    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("name") String name) {
        // Spelled out: Path alone is the JAX-RS annotation here.
        java.nio.file.Path file = backups.file(name).orElseThrow(() ->
                ApiException.notFound("backup_not_found", "Backup not found."));
        return Response.ok(file.toFile(), MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", ContentDisposition.attachment(name))
                .build();
    }
}
