package dev.vitral.user.auth;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
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

import java.util.List;
import java.util.UUID;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService auth;

    @Inject
    SessionService sessions;

    @Inject
    CurrentUser currentUser;

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResult login(@Valid LoginRequest request) {
        return auth.login(request.email(), request.password(), request.deviceName());
    }

    /** Ends the session used by this request. */
    @POST
    @Path("/logout")
    @Authenticated
    public void logout() {
        AuthenticatedUser me = currentUser.get();
        sessions.revoke(me.userId(), me.sessionId());
    }

    /** Lists the devices logged into this account. */
    @GET
    @Path("/sessions")
    @Authenticated
    public List<SessionView> listSessions() {
        AuthenticatedUser me = currentUser.get();
        return sessions.listActive(me.userId()).stream()
                .map(session -> SessionView.of(session, me.sessionId()))
                .toList();
    }

    /** Logs out a device, for example a lost phone. */
    @DELETE
    @Path("/sessions/{id}")
    @Authenticated
    public void revokeSession(@PathParam("id") UUID id) {
        sessions.revoke(currentUser.get().userId(), id);
    }
}
