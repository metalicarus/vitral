package com.genosha.vitral.user;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Timestamps;
import com.genosha.vitral.user.auth.PasswordHasher;
import com.genosha.vitral.user.auth.Session;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 256;
    private static final int MAX_NAME = 100;

    @Inject
    PasswordHasher passwordHasher;

    @Transactional
    public User create(String email, String displayName, String password, Role role) {
        String normalized = User.normalizeEmail(email);
        if (User.findByEmail(normalized).isPresent()) {
            throw ApiException.conflict("email_taken", "A user with this e-mail already exists.");
        }
        User user = new User();
        user.email = normalized;
        user.displayName = displayName.trim();
        user.passwordHash = passwordHasher.hash(password);
        user.role = role;
        user.createdAt = Timestamps.now();
        user.persist();
        LOG.infof("Account %s created (%s)", user.email, role);
        return user;
    }

    public List<User> listAll() {
        return User.listAll(Sort.ascending("createdAt"));
    }

    /** Changes the name shown for the user. */
    @Transactional
    public User rename(UUID userId, String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw ApiException.badRequest("invalid_name", "The name must have 1 to 100 characters.");
        }
        User user = existing(userId);
        LOG.infof("%s changed their display name", user.email);
        user.displayName = name;
        return user;
    }

    /**
     * Changes the user's own password, after checking the current one. Every other session of
     * the user ends, so a password that leaked stops working everywhere; the one making the
     * change stays logged in.
     */
    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        User user = existing(userId);
        // 400 rather than 401: a client takes 401 as "session expired" and would log the user out.
        if (!passwordHasher.verify(currentPassword, user.passwordHash)) {
            LOG.warnf("%s typed a wrong current password while changing it", user.email);
            throw ApiException.badRequest("wrong_password", "The current password is wrong.");
        }
        user.passwordHash = passwordHasher.hash(validPassword(newPassword));
        long ended = Session.delete("user.id = ?1 and id <> ?2", userId, currentSessionId);
        LOG.infof("%s changed their password; %d other session(s) ended", user.email, ended);
    }

    /** An administrator sets a new password for someone who forgot theirs; all of that user's sessions end. */
    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User user = existing(userId);
        user.passwordHash = passwordHasher.hash(validPassword(newPassword));
        long ended = Session.delete("user.id", userId);
        LOG.infof("Password of %s reset; %d session(s) ended", user.email, ended);
    }

    /**
     * Changes an account's role or disables it; null leaves that as it is. Administrators cannot
     * change their own account this way, so whoever makes a change stays an active administrator
     * and the server is never left without one.
     */
    @Transactional
    public User change(UUID adminId, UUID userId, Boolean admin, Boolean disabled) {
        User user = existing(userId);
        if (userId.equals(adminId)) {
            throw ApiException.conflict("cannot_change_self",
                    "Administrators cannot change their own role or disable themselves.");
        }
        if (admin != null) {
            Role role = admin ? Role.ADMIN : Role.USER;
            if (role != user.role) {
                LOG.infof("%s is now %s", user.email, role);
                user.role = role;
                // Sessions carry the role, so the change applies on the next login.
                Session.delete("user.id", userId);
            }
        }
        if (disabled != null && disabled != user.disabled) {
            user.disabled = disabled;
            if (disabled) {
                long ended = Session.delete("user.id", userId);
                LOG.infof("Account %s disabled; %d session(s) ended", user.email, ended);
            } else {
                LOG.infof("Account %s enabled again", user.email);
            }
        }
        return user;
    }

    private static User existing(UUID userId) {
        User user = User.findById(userId);
        if (user == null) {
            throw ApiException.notFound("user_not_found", "User not found.");
        }
        return user;
    }

    private static String validPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw ApiException.badRequest("invalid_password", "The password must have 8 to 256 characters.");
        }
        return password;
    }
}
