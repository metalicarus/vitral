package com.genosha.vitral.user;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminUserResource {

    @Inject
    UserService users;

    @GET
    public List<UserView> list() {
        return users.listAll().stream().map(UserView::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid CreateUserRequest request) {
        Role role = request.admin() ? Role.ADMIN : Role.USER;
        User user = users.create(request.email(), request.displayName(), request.password(), role);
        return Response.status(Response.Status.CREATED).entity(UserView.of(user)).build();
    }
}
