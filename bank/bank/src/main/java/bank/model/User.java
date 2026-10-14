package bank.model;

import java.util.Objects;

/**
 * A login user. The password itself is never stored, only a PBKDF2 hash and its salt.
 *
 * @param saltBase64         per-user random salt, Base64
 * @param passwordHashBase64 PBKDF2WithHmacSHA256 output, Base64
 * @param iterations         PBKDF2 iteration count used for this hash (stored so it can be raised later)
 * @param mustChangePassword true until the user replaces an initial/default password
 */
public record User(String username,
                   String saltBase64,
                   String passwordHashBase64,
                   int iterations,
                   boolean mustChangePassword) {

    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(saltBase64, "saltBase64");
        Objects.requireNonNull(passwordHashBase64, "passwordHashBase64");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
    }

    @Override
    public String toString() {
        // Keep hash material out of logs.
        return "User[" + username + "]";
    }
}
