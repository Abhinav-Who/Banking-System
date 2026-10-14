package bank.service;

import bank.model.User;
import bank.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Login, first-run admin bootstrap, password changes and user management.
 *
 * <p>Two levels of user: everyone can do banking work; administrators can also add users, reset
 * their passwords and disable or re-enable them. These checks are enforced here, not in the UI.
 */
public final class AuthService {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final String INVALID_LOGIN = "Invalid username or password.";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{2,31}");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Actors actors;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
        this.actors = new Actors(users);
        this.clock = Objects.requireNonNull(clock);
    }

    /** On first run (no users at all) creates admin/admin123, flagged to change password at first login. */
    public void ensureDefaultAdmin() {
        if (users.count() == 0) {
            char[] password = DEFAULT_ADMIN_PASSWORD.toCharArray();
            users.save(newUser(DEFAULT_ADMIN_USERNAME, password, true));
            Arrays.fill(password, '\0');
        }
    }

    // ---- signing in ----------------------------------------------------------------------

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
        // Checked after the password, so only someone who knows it learns the account is disabled.
        if (!user.get().active()) {
            throw new BankException("This user has been disabled. Ask an administrator to re-enable it.");
        }
        return user.get();
    }

    /** Replaces your own password after verifying the current one, and clears the must-change flag. */
    public User changePassword(String username, char[] currentPassword, char[] newPassword, char[] confirmation) {
        User user = actors.requireActive(username);
        if (currentPassword == null || !hasher.matches(user, currentPassword)) {
            throw new BankException("Current password is incorrect.");
        }
        validateNewPassword(newPassword, confirmation);
        if (Arrays.equals(newPassword, currentPassword)) {
            throw new BankException("New password must be different from the current one.");
        }
        User updated = withNewPassword(user, newPassword, false);
        users.save(updated);
        return updated;
    }

    // ---- user management (administrators only) -------------------------------------------

    public List<User> listUsers(String actor) {
        actors.requireAdmin(actor);
        return users.findAll();
    }

    /**
     * Creates a user with a temporary password they must change at first login.
     */
    public User createUser(String actor, String username, char[] temporaryPassword, char[] confirmation,
                           boolean admin) {
        actors.requireAdmin(actor);
        String name = validateUsername(username);
        if (users.findByUsername(name).isPresent()) {
            throw new BankException("A user named " + name + " already exists (usernames are not case-sensitive).");
        }
        validateNewPassword(temporaryPassword, confirmation);
        User user = newUser(name, temporaryPassword, admin);
        users.save(user);
        return user;
    }

    /**
     * Sets a temporary password for another user, who must change it at next login. Use
     * {@link #changePassword} for your own password, which also checks the current one.
     */
    public User resetPassword(String actor, String username, char[] temporaryPassword, char[] confirmation) {
        User admin = actors.requireAdmin(actor);
        User target = requireUser(username);
        if (target.username().equalsIgnoreCase(admin.username())) {
            throw new BankException("Use \"Change password\" to change your own password.");
        }
        validateNewPassword(temporaryPassword, confirmation);
        User updated = withNewPassword(target, temporaryPassword, true);
        users.save(updated);
        return updated;
    }

    /**
     * Disables or re-enables a user. Disabled users cannot sign in but stay on record, so their
     * name remains in the transaction history.
     *
     * <p>You cannot disable yourself. That alone guarantees at least one active administrator
     * always remains: only an active administrator can disable anyone, and they are never the target.
     */
    public User setActive(String actor, String username, boolean active) {
        User admin = actors.requireAdmin(actor);
        User target = requireUser(username);
        if (target.active() == active) {
            return target;
        }
        if (!active && target.username().equalsIgnoreCase(admin.username())) {
            throw new BankException("You cannot disable your own user.");
        }
        User updated = target.withActive(active);
        users.save(updated);
        return updated;
    }

    // ---- helpers -------------------------------------------------------------------------

    private User requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new BankException("Please choose a user.");
        }
        return users.findByUsername(username.trim())
                .orElseThrow(() -> new BankException("No user named " + username.trim() + "."));
    }

    private static String validateUsername(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new BankException("Please enter a username.");
        }
        if (!USERNAME.matcher(name).matches()) {
            throw new BankException("Username must be 3 to 32 characters, start with a letter, and use only "
                    + "letters, digits, '.', '_' or '-'.");
        }
        return name;
    }

    private static void validateNewPassword(char[] password, char[] confirmation) {
        if (password == null || password.length == 0) {
            throw new BankException("Please enter a new password.");
        }
        if (confirmation == null || !Arrays.equals(password, confirmation)) {
            throw new BankException("The new password and its confirmation do not match.");
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new BankException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            throw new BankException("Password can be at most " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (Arrays.equals(password, DEFAULT_ADMIN_PASSWORD.toCharArray())) {
            throw new BankException("You cannot use the default password.");
        }
    }

    private User newUser(String username, char[] password, boolean admin) {
        PasswordHasher.Hash h = hasher.hash(password);
        return new User(username, h.saltBase64(), h.hashBase64(), h.iterations(), true, admin, true,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    private User withNewPassword(User user, char[] password, boolean mustChange) {
        PasswordHasher.Hash h = hasher.hash(password);
        return user.withPassword(h.saltBase64(), h.hashBase64(), h.iterations(), mustChange);
    }
}
