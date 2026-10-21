package com.genosha.vitral.support;

import com.genosha.vitral.user.Role;
import com.genosha.vitral.user.User;
import com.genosha.vitral.user.UserService;
import com.genosha.vitral.user.auth.SessionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Creates users and sessions directly, so tests don't depend on the one-time setup endpoint. */
@ApplicationScoped
public class TestUsers {

    @Inject
    UserService users;

    @Inject
    SessionService sessions;

    /** A fresh token for the user with this e-mail, creating the user on first use. */
    @Transactional
    public String tokenFor(String email) {
        User user = User.findByEmail(email)
                .orElseGet(() -> users.create(email, "Test user", "senha-de-teste-123", Role.USER));
        return sessions.create(user, "test").token();
    }
}