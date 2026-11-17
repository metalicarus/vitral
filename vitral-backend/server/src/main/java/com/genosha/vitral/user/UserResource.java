package com.genosha.vitral.user;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.auth.AuthenticatedUser;
import com.genosha.vitral.user.auth.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The logged-in user's own account. */
@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserService users;

    /** Body of PATCH /api/v1/users/me. */
    public record ProfileChanges(String displayName) {
    }

    /** Body of POST /api/v1/users/me/password. */
    public record PasswordChange(String currentPassword, String newPassword) {
    }

    @GET
    @Path("/me")
    public UserView me() {
        User user = User.findById(currentUser.get().userId());
        if (user == null) {
            throw ApiException.unauthorized("not_authenticated", "Authentication is required.");
        }
        return UserView.of(user);
    }

    /** Changes the name shown for the user. */
    @PATCH
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserView changeProfile(ProfileChanges changes) {
        return UserView.of(users.rename(currentUser.get().userId(), changes == null ? null : changes.displayName()));
    }

    /**
     * Changes the password, after checking the current one (400 wrong_password otherwise). The
     * user's other sessions end; this one stays.
     */
    @POST
    @Path("/me/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public void changePassword(PasswordChange change) {
        if (change == null) {
            throw ApiException.badRequest("missing_password", "Send currentPassword and newPassword.");
        }
        AuthenticatedUser me = currentUser.get();
        users.changePassword(me.userId(), me.sessionId(), change.currentPassword(), change.newPassword());
    }
}
