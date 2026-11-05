package com.genosha.vitral.user.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Turns a bearer token into a SecurityIdentity by looking up its session in the database. */
@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    @Inject
    SessionService sessions;

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        String token = request.getToken().getToken();
        // The lookup hits the database, so it must run off the event loop.
        return context.runBlocking(() -> sessions.resolve(token)
                .map(user -> (SecurityIdentity) QuarkusSecurityIdentity.builder()
                        .setPrincipal(new QuarkusPrincipal(user.userId().toString()))
                        .addRole(user.role().name())
                        .addAttribute(AuthenticatedUser.ATTRIBUTE, user)
                        .build())
                .orElseThrow(AuthenticationFailedException::new));
    }
}
