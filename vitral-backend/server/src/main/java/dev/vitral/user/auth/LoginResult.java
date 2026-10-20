package dev.vitral.user.auth;

import dev.vitral.user.UserView;

import java.time.Instant;

/** Returned after login or setup. The token goes in the header {@code Authorization: Bearer <token>}. */
public record LoginResult(String token, Instant expiresAt, UserView user) {
}
