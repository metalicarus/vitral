package dev.vitral.user.auth;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;

/** Reads {@code Authorization: Bearer <token>} and hands it to {@link SessionIdentityProvider}. */
@ApplicationScoped
public class BearerTokenAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String PREFIX = "Bearer ";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String header = context.request().getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            // No token: the request continues as anonymous.
            return Uni.createFrom().optional(Optional.empty());
        }
        String token = header.substring(PREFIX.length()).trim();
        return identityProviderManager.authenticate(
                new TokenAuthenticationRequest(new TokenCredential(token, "bearer")));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(TokenAuthenticationRequest.class);
    }
}
