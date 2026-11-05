package com.genosha.vitral.user;

import java.time.Instant;
import java.util.UUID;

/** What the API exposes about a user. Never includes the password hash. */
public record UserView(UUID id, String email, String displayName, Role role, Instant createdAt) {

    public static UserView of(User user) {
        return new UserView(user.id, user.email, user.displayName, user.role, user.createdAt);
    }
}
