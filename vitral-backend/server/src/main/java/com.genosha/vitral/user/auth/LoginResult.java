package com.genosha.vitral.user.auth;

import com.genosha.vitral.user.UserView;

import java.time.Instant;

/** Returned after login or setup. The token goes in the header {@code Authorization: Bearer <token>}. */
public record LoginResult(String token, Instant expiresAt, UserView user) {
}
