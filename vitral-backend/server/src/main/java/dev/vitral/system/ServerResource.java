package dev.vitral.system;

import dev.vitral.user.User;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/v1/server")
@Produces(MediaType.APPLICATION_JSON)
public class ServerResource {

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    /** Public information clients need before logging in. */
    public record ServerInfo(String name, String version, boolean setupRequired) {
    }

    @GET
    @Path("/info")
    @PermitAll
    public ServerInfo info() {
        return new ServerInfo("Vitral", version, User.count() == 0);
    }
}
