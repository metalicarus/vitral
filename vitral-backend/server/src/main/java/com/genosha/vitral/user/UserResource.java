package com.genosha.vitral.user;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @GET
    @Path("/me")
    public UserView me() {
        User user = User.findById(currentUser.get().userId());
        if (user == null) {
            throw ApiException.unauthorized("not_authenticated", "Authentication is required.");
        }
        return UserView.of(user);
    }
}
