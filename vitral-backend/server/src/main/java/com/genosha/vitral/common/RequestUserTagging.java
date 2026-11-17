package com.genosha.vitral.common;

import com.genosha.vitral.user.auth.AuthenticatedUser;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Puts the e-mail of the user behind each request in the logging context ({@code %X{user}}
 * in the log format), so every line logged while handling it says who it was for.
 */
public class RequestUserTagging {

    static final String KEY = "user";

    @Inject
    SecurityIdentity identity;

    @ServerRequestFilter
    public void tag() {
        AuthenticatedUser user = identity.isAnonymous() ? null : identity.getAttribute(AuthenticatedUser.ATTRIBUTE);
        if (user != null) {
            MDC.put(KEY, user.email());
        } else {
            MDC.remove(KEY);
        }
    }

    @ServerResponseFilter
    public void untag() {
        MDC.remove(KEY);
    }
}
