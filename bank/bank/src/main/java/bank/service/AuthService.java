package bank.service;

import bank.model.User;
import bank.repository.UserRepository;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Login, first-run admin bootstrap and password changes. */
public final class AuthService {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final String INVALID_LOGIN = "Invalid username or password.";

    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthService(UserRepository users, PasswordHasher hasher) {
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
    }

    /** On first run (no users at all) creates admin/admin123, flagged to change password at first login. */
    public void ensureDefaultAdmin() {
        if (users.count() == 0) {
            char[] password = DEFAULT_ADMIN_PASSWORD.toCharArray();
            users.save(hasher.newUser(DEFAULT_ADMIN_USERNAME, password, true));
            Arrays.fill(password, '\0');
        }
    }

    /**
     * @return the user; check {@link User#mustChangePassword()} before letting them in
     * @throws BankException with a deliberately vague message if the credentials are wrong
     */
    public User login(String username, char[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new BankException("Please enter your username and password.");
        }
        Optional<User> user = users.findByUsername(username.trim());
        if (user.isEmpty()) {
            hasher.burnEquivalentTime(password);
            throw new BankException(INVALID_LOGIN);
        }
        if (!hasher.matches(user.get(), password)) {
            throw new BankException(INVALID_LOGIN);
        }
        return user.get();
    }

    /**
     * Replaces the password after verifying the current one, and clears the must-change flag.
     */
    public User changePassword(String username, char[] currentPassword, char[] newPassword, char[] confirmation) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new BankException("User " + username + " no longer exists."));
        if (currentPassword == null || !hasher.matches(user, currentPassword)) {
            throw new BankException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length == 0) {
            throw new BankException("Please enter a new password.");
        }
        if (confirmation == null || !Arrays.equals(newPassword, confirmation)) {
            throw new BankException("The new password and its confirmation do not match.");
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            throw new BankException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (newPassword.length > MAX_PASSWORD_LENGTH) {
            throw new BankException("New password can be at most " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (Arrays.equals(newPassword, currentPassword)) {
            throw new BankException("New password must be different from the current one.");
        }
        if (Arrays.equals(newPassword, DEFAULT_ADMIN_PASSWORD.toCharArray())) {
            throw new BankException("You cannot reuse the default password.");
        }
        User updated = hasher.newUser(user.username(), newPassword, false);
        users.save(updated);
        return updated;
    }
}
