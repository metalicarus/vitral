package dev.vitral.user;

import dev.vitral.common.ApiException;
import dev.vitral.user.auth.PasswordHasher;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class UserService {

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
        user.createdAt = Instant.now();
        user.persist();
        return user;
    }

    public List<User> listAll() {
        return User.listAll(Sort.ascending("createdAt"));
    }
}
