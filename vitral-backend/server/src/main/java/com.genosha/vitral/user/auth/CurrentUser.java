package com.genosha.vitral.user.auth;

import com.genosha.vitral.common.ApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/** Gives endpoints access to the user behind the current request. */
@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    public AuthenticatedUser get() {
        AuthenticatedUser user = identity.getAttribute(AuthenticatedUser.ATTRIBUTE);
        if (user == null) {
            throw ApiException.unauthorized("not_authenticated", "Authentication is required.");
        }
        return user;
    }
}
