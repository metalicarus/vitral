package com.genosha.vitral.user.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** First-run setup: creates the administrator and logs them in. */
@Path("/api/v1/setup")
public class SetupResource {

    @Inject
    AuthService auth;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setup(@Valid SetupRequest request) {
        LoginResult result = auth.setup(request.email(), request.displayName(), request.password());
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
