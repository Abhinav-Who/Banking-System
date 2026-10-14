package bank.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A staff login. The password itself is never stored, only a PBKDF2 hash and its salt.
 *
 * @param saltBase64         per-user random salt, Base64
 * @param passwordHashBase64 PBKDF2WithHmacSHA256 output, Base64
 * @param iterations         PBKDF2 iteration count used for this hash (stored so it can be raised later)
 * @param mustChangePassword true until the user replaces an initial, default or reset password
 * @param admin              administrators can also manage users
 * @param active             disabled users cannot log in; they stay on record for the audit trail
 * @param createdAt          null for users created before this field existed
 */
public record User(String username,
                   String saltBase64,
                   String passwordHashBase64,
                   int iterations,
                   boolean mustChangePassword,
                   boolean admin,
                   boolean active,
                   LocalDateTime createdAt) {

    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(saltBase64, "saltBase64");
        Objects.requireNonNull(passwordHashBase64, "passwordHashBase64");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
    }

    public User withPassword(String saltBase64, String passwordHashBase64, int iterations, boolean mustChangePassword) {
        return new User(username, saltBase64, passwordHashBase64, iterations, mustChangePassword, admin, active, createdAt);
    }

    public User withActive(boolean active) {
        return new User(username, saltBase64, passwordHashBase64, iterations, mustChangePassword, admin, active, createdAt);
    }

    @Override
    public String toString() {
        // Keep hash material out of logs.
        return "User[" + username + (admin ? ", admin" : "") + (active ? "" : ", disabled") + "]";
    }
}
