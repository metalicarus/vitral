package com.genosha.vitral.user.auth;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.user.Role;
import com.genosha.vitral.user.User;
import com.genosha.vitral.user.UserService;
import com.genosha.vitral.user.UserView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    SessionService sessions;

    @Inject
    UserService users;

    @Inject
    LoginThrottle throttle;

    @Transactional
    public LoginResult login(String email, String password, String deviceName) {
        String key = User.normalizeEmail(email);
        if (throttle.isLocked(key)) {
            LOG.warnf("Login refused for %s: locked after too many failed attempts", key);
            throw ApiException.tooManyRequests("login_locked", "Too many failed attempts. Try again later.");
        }
        Optional<User> user = User.findByEmail(key);
        boolean valid;
        if (user.isPresent()) {
            valid = passwordHasher.verify(password, user.get().passwordHash);
        } else {
            passwordHasher.simulateVerify(password);
            valid = false;
        }
        if (!valid) {
            throttle.recordFailure(key);
            // The e-mail is logged to spot attacks; whether it exists is not, so the log reveals no accounts.
            LOG.warnf("Failed login for %s", key);
            throw ApiException.unauthorized("invalid_credentials", "E-mail or password is incorrect.");
        }
        throttle.reset(key);
        LOG.infof("%s logged in%s", key, deviceName == null || deviceName.isBlank() ? "" : " from " + deviceName.strip());
        return issue(user.get(), deviceName);
    }

    /** Creates the first administrator. Works only while the server has no users. */
    @Transactional
    public LoginResult setup(String email, String displayName, String password) {
        // Transactions start with BEGIN IMMEDIATE (see application.properties), so SQLite
        // runs concurrent setup attempts one after the other: only the first creates an admin.
        if (User.count() > 0) {
            throw ApiException.conflict("setup_done", "The server is already set up.");
        }
        User admin = users.create(email, displayName, password, Role.ADMIN);
        LOG.infof("First-run setup: administrator %s created", admin.email);
        return issue(admin, "Setup");
    }

    private LoginResult issue(User user, String deviceName) {
        SessionService.IssuedSession issued = sessions.create(user, deviceName);
        return new LoginResult(issued.token(), issued.session().expiresAt, UserView.of(user));
    }
}