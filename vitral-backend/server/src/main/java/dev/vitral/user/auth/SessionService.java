package dev.vitral.user.auth;

import dev.vitral.common.ApiException;
import dev.vitral.config.VitralConfig;
import dev.vitral.user.User;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SessionService {

    /** lastUsedAt is refreshed at most this often, to avoid a database write per request. */
    private static final Duration LAST_USED_GRANULARITY = Duration.ofMinutes(1);

    @Inject
    VitralConfig config;

    /** A new session plus its token, which is shown to the client only once. */
    public record IssuedSession(Session session, String token) {
    }

    @Transactional
    public IssuedSession create(User user, String deviceName) {
        String token = SessionTokens.newToken();
        Instant now = Instant.now();
        Session session = new Session();
        session.user = user;
        session.tokenHash = SessionTokens.hash(token);
        session.deviceName = deviceName == null || deviceName.isBlank() ? null : deviceName.trim();
        session.createdAt = now;
        session.lastUsedAt = now;
        session.expiresAt = now.plus(config.session().ttl());
        session.persist();
        return new IssuedSession(session, token);
    }

    /** Finds the user behind a token, or empty if the token is unknown or expired. */
    @Transactional
    public Optional<AuthenticatedUser> resolve(String token) {
        Optional<Session> found = Session
                .find("from Session s join fetch s.user where s.tokenHash = ?1", SessionTokens.hash(token))
                .firstResultOptional();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        Instant now = Instant.now();
        if (!session.expiresAt.isAfter(now)) {
            session.delete();
            return Optional.empty();
        }
        if (Duration.between(session.lastUsedAt, now).compareTo(LAST_USED_GRANULARITY) >= 0) {
            session.lastUsedAt = now;
        }
        User user = session.user;
        return Optional.of(new AuthenticatedUser(user.id, session.id, user.email, user.role));
    }

    public List<Session> listActive(UUID userId) {
        return Session.list("user.id = ?1 and expiresAt > ?2",
                Sort.descending("lastUsedAt"), userId, Instant.now());
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        Session session = Session.findById(sessionId);
        if (session == null || !session.user.id.equals(userId)) {
            throw ApiException.notFound("session_not_found", "Session not found.");
        }
        session.delete();
    }
}
