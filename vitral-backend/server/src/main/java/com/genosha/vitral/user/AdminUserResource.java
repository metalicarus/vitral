package com.genosha.vitral.user;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.auth.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminUserResource {

    @Inject
    UserService users;

    @Inject
    CurrentUser currentUser;

    /** Body of PATCH /api/v1/admin/users/{id}; fields left out keep their value. */
    public record AccountChanges(Boolean admin, Boolean disabled) {
    }

    /** Body of POST /api/v1/admin/users/{id}/password. */
    public record NewPassword(String newPassword) {
    }

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

    /**
     * Makes the account an administrator or a member, or disables it (it can no longer log in)
     * or enables it again. Not for one's own account, which keeps the server with at least one
     * active administrator.
     */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserView change(@PathParam("id") String id, AccountChanges changes) {
        if (changes == null || (changes.admin() == null && changes.disabled() == null)) {
            throw ApiException.badRequest("missing_changes", "Send admin and/or disabled.");
        }
        return UserView.of(users.change(currentUser.get().userId(), userId(id), changes.admin(), changes.disabled()));
    }

    /** Sets a new password for someone who forgot theirs. All of their sessions end. */
    @POST
    @Path("/{id}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void resetPassword(@PathParam("id") String id, NewPassword request) {
        users.resetPassword(userId(id), request == null ? null : request.newPassword());
    }

    private static UUID userId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("user_not_found", "User not found.");
        }
    }
}
