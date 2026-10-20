package dev.vitral.user.auth;

import dev.vitral.user.Role;

import java.util.UUID;

/** The caller of the current request, attached to the Quarkus SecurityIdentity. */
public record AuthenticatedUser(UUID userId, UUID sessionId, String email, Role role) {

    public static final String ATTRIBUTE = "vitral.user";
}
