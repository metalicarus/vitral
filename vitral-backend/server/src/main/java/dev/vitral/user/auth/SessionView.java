package dev.vitral.user.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionView(UUID id, String deviceName, Instant createdAt, Instant lastUsedAt,
                          Instant expiresAt, boolean current) {

    public static SessionView of(Session session, UUID currentSessionId) {
        return new SessionView(session.id, session.deviceName, session.createdAt, session.lastUsedAt,
                session.expiresAt, session.id.equals(currentSessionId));
    }
}
