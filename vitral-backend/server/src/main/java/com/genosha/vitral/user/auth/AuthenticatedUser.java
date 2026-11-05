package com.genosha.vitral.user.auth;

import com.genosha.vitral.user.Role;

import java.util.UUID;

/** The caller of the current request, attached to the Quarkus SecurityIdentity. */
public record AuthenticatedUser(UUID userId, UUID sessionId, String email, Role role) {

    public static final String ATTRIBUTE = "vitral.user";
}
