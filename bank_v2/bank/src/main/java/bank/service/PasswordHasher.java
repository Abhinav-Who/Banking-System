package bank.service;

import bank.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2WithHmacSHA256 with a 16-byte random salt per user. Uses only the JDK. */
public final class PasswordHasher {

    /** OWASP's 2023 guidance for PBKDF2-HMAC-SHA256 is 600,000; see README for why this is lower. */
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Output of hashing one password. */
    public record Hash(String saltBase64, String hashBase64, int iterations) {
    }

    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    private final byte[] dummySalt = new byte[SALT_BYTES];

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** Lower iteration counts are for tests only. */
    public PasswordHasher(int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
        random.nextBytes(dummySalt);
    }

    /** Hashes with a fresh random salt. */
    public Hash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return new Hash(b64.encodeToString(salt), b64.encodeToString(hash), iterations);
    }

    public boolean matches(User user, char[] password) {
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] expected = b64.decode(user.passwordHashBase64());
        byte[] actual = derive(password, b64.decode(user.saltBase64()), user.iterations());
        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    /** Spends the same time as a real check, so unknown usernames are not revealed by timing. */
    void burnEquivalentTime(char[] password) {
        derive(password, dummySalt, iterations);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available in this JDK", e);
        } finally {
            spec.clearPassword();
        }
    }
}
