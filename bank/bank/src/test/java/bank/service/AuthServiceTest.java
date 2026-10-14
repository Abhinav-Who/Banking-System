package bank.service;

import bank.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    @TempDir
    Path dir;

    private TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(dir);
        bank.auth.ensureDefaultAdmin();
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void firstRunCreatesAdminWhoMustChangePassword() {
        User admin = bank.auth.login("admin", pw("admin123"));
        assertTrue(admin.mustChangePassword());
        bank.auth.ensureDefaultAdmin(); // idempotent
        assertEquals(1, bank.userRepo.count());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        BankException wrong = assertThrows(BankException.class, () -> bank.auth.login("admin", pw("nope")));
        BankException unknown = assertThrows(BankException.class, () -> bank.auth.login("ghost", pw("admin123")));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertThrows(BankException.class, () -> bank.auth.login("", pw("x")));
        assertThrows(BankException.class, () -> bank.auth.login("admin", pw("")));
    }

    @Test
    void usernameIsCaseInsensitive() {
        bank.auth.login("ADMIN", pw("admin123"));
    }

    @Test
    void changePasswordEnforcesRules() {
        assertThrows(BankException.class,
                () -> bank.auth.changePassword("admin", pw("wrong"), pw("newpass123"), pw("newpass123")));
        assertThrows(BankException.class,
                () -> bank.auth.changePassword("admin", pw("admin123"), pw("newpass123"), pw("different1")));
        assertThrows(BankException.class,
                () -> bank.auth.changePassword("admin", pw("admin123"), pw("short"), pw("short")));
        assertThrows(BankException.class,
                () -> bank.auth.changePassword("admin", pw("admin123"), pw("admin123"), pw("admin123")));
        // Still the default after all those failures.
        assertTrue(bank.auth.login("admin", pw("admin123")).mustChangePassword());
    }

    @Test
    void changedPasswordWorksPersistsAndClearsFlag() throws Exception {
        bank.auth.changePassword("admin", pw("admin123"), pw("S3cure-pass"), pw("S3cure-pass"));

        TestBank restarted = bank.reopen();
        User admin = restarted.auth.login("admin", pw("S3cure-pass"));
        assertFalse(admin.mustChangePassword());
        assertThrows(BankException.class, () -> restarted.auth.login("admin", pw("admin123")));

        String file = Files.readString(dir.resolve("users.csv"));
        assertFalse(file.contains("S3cure-pass"));
        assertFalse(file.contains("admin123"));
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        PasswordHasher hasher = new PasswordHasher(1_000);
        User a = hasher.newUser("a", pw("same-password"), false);
        User b = hasher.newUser("b", pw("same-password"), false);
        assertNotEquals(a.saltBase64(), b.saltBase64());
        assertNotEquals(a.passwordHashBase64(), b.passwordHashBase64());
        assertTrue(hasher.matches(a, pw("same-password")));
        assertFalse(hasher.matches(a, pw("same-passwore")));
    }
}
