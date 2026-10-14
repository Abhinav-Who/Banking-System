package bank.service;

import bank.model.Account;
import bank.model.AccountType;
import bank.model.Transaction;
import bank.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementTest {

    private static final String ADMIN = TestBank.ADMIN;

    @TempDir
    Path dir;

    private TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(dir);
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    private User createStaff(String name) {
        return bank.auth.createUser(ADMIN, name, pw("Temp-pass1"), pw("Temp-pass1"), false);
    }

    @Test
    void adminCreatesStaffWhoMustChangeTemporaryPassword() {
        User created = createStaff("ravi");
        assertFalse(created.admin());
        assertTrue(created.active());
        assertTrue(created.mustChangePassword());
        assertEquals(TestBank.CLOCK.instant().toString().substring(0, 16),
                created.createdAt().toString().substring(0, 16));

        User loggedIn = bank.auth.login("ravi", pw("Temp-pass1"));
        assertTrue(loggedIn.mustChangePassword());
        bank.auth.changePassword("ravi", pw("Temp-pass1"), pw("Ravi-own-pw"), pw("Ravi-own-pw"));
        assertFalse(bank.auth.login("ravi", pw("Ravi-own-pw")).mustChangePassword());
        assertEquals(2, bank.auth.listUsers(ADMIN).size());
    }

    @Test
    void staffCannotManageUsersEvenByCallingTheServiceDirectly() {
        createStaff("ravi");
        assertThrows(BankException.class, () -> bank.auth.listUsers("ravi"));
        assertThrows(BankException.class,
                () -> bank.auth.createUser("ravi", "mallory", pw("Temp-pass1"), pw("Temp-pass1"), true));
        assertThrows(BankException.class,
                () -> bank.auth.resetPassword("ravi", ADMIN, pw("Hijacked-1"), pw("Hijacked-1")));
        assertThrows(BankException.class, () -> bank.auth.setActive("ravi", ADMIN, false));
        assertEquals(2, bank.userRepo.count());
    }

    @Test
    void secondAdminCanManageUsers() {
        bank.auth.createUser(ADMIN, "priya", pw("Temp-pass1"), pw("Temp-pass1"), true);
        createStaff("ravi");
        bank.auth.resetPassword("priya", "ravi", pw("Another-1"), pw("Another-1"));
        bank.auth.login("ravi", pw("Another-1"));
    }

    @Test
    void usernamesAreValidatedAndUniqueIgnoringCase() {
        createStaff("ravi");
        assertThrows(BankException.class, () -> createStaff("RAVI"));
        for (String bad : new String[]{"", "ab", "1abc", "has space", "semi;colon", "x".repeat(33)}) {
            assertThrows(BankException.class, () -> createStaff(bad), "should reject '" + bad + "'");
        }
        assertThrows(BankException.class,
                () -> bank.auth.createUser(ADMIN, "weak", pw("short"), pw("short"), false));
        assertThrows(BankException.class,
                () -> bank.auth.createUser(ADMIN, "weak", pw("Temp-pass1"), pw("Temp-pass2"), false));
        assertEquals(2, bank.userRepo.count());
    }

    @Test
    void resetPasswordForcesChangeAndInvalidatesOldPassword() {
        createStaff("ravi");
        bank.auth.changePassword("ravi", pw("Temp-pass1"), pw("Ravi-own-pw"), pw("Ravi-own-pw"));

        bank.auth.resetPassword(ADMIN, "ravi", pw("Reset-pass1"), pw("Reset-pass1"));

        assertThrows(BankException.class, () -> bank.auth.login("ravi", pw("Ravi-own-pw")));
        assertTrue(bank.auth.login("ravi", pw("Reset-pass1")).mustChangePassword());
        // Role and status survive a password reset.
        User ravi = bank.userRepo.findByUsername("ravi").orElseThrow();
        assertFalse(ravi.admin());
        assertTrue(ravi.active());
    }

    @Test
    void adminCannotResetOwnPasswordWithoutKnowingIt() {
        assertThrows(BankException.class,
                () -> bank.auth.resetPassword(ADMIN, ADMIN, pw("Reset-pass1"), pw("Reset-pass1")));
    }

    @Test
    void disabledUserCannotSignInOrTransactUntilReEnabled() {
        createStaff("ravi");
        String acct = bank.accounts.openAccount(ADMIN, "C", "9876543210", AccountType.CURRENT, new BigDecimal("10"))
                .getAccountNumber();

        bank.auth.setActive(ADMIN, "ravi", false);

        BankException e = assertThrows(BankException.class, () -> bank.auth.login("ravi", pw("Temp-pass1")));
        assertTrue(e.getMessage().contains("disabled"), e.getMessage());
        assertThrows(BankException.class, () -> bank.transactions.deposit("ravi", acct, new BigDecimal("5")));
        assertEquals(1, bank.transactions.history(acct).size());

        bank.auth.setActive(ADMIN, "ravi", true);
        bank.auth.login("ravi", pw("Temp-pass1"));
        bank.transactions.deposit("ravi", acct, new BigDecimal("5"));
    }

    @Test
    void wrongPasswordForDisabledUserDoesNotRevealThatItIsDisabled() {
        createStaff("ravi");
        bank.auth.setActive(ADMIN, "ravi", false);
        BankException e = assertThrows(BankException.class, () -> bank.auth.login("ravi", pw("wrong-pass")));
        assertFalse(e.getMessage().contains("disabled"));
    }

    @Test
    void adminCannotDisableThemselves() {
        assertThrows(BankException.class, () -> bank.auth.setActive(ADMIN, ADMIN, false));
        assertTrue(bank.auth.login(ADMIN, pw("admin123")).active());
    }

    @Test
    void everyChangeRecordsWhoDidIt() {
        createStaff("ravi");
        Account a = bank.accounts.openAccount("ravi", "C", "9876543210", AccountType.CURRENT, new BigDecimal("100"));
        Account b = bank.accounts.openAccount(ADMIN, "D", "9876543211", AccountType.CURRENT, new BigDecimal("100"));
        bank.transactions.deposit(ADMIN, a.getAccountNumber(), new BigDecimal("1"));
        bank.transactions.transfer("RAVI", a.getAccountNumber(), b.getAccountNumber(), new BigDecimal("2"));

        assertEquals("ravi", a.getOpenedBy());
        List<Transaction> history = bank.reopen().transactions.history(a.getAccountNumber());
        assertEquals(List.of("ravi", ADMIN, "ravi"), history.stream().map(Transaction::performedBy).toList());
        // Stored with the user's own spelling even if typed differently.
        Transaction in = bank.transactions.history(b.getAccountNumber()).get(1);
        assertEquals("ravi", in.performedBy());
        assertEquals("ravi", bank.reopen().accounts.getAccount(a.getAccountNumber()).getOpenedBy());
    }

    @Test
    void unknownOrMissingActorIsRejected() {
        String acct = bank.accounts.openAccount(ADMIN, "C", "9876543210", AccountType.CURRENT, new BigDecimal("10"))
                .getAccountNumber();
        assertThrows(BankException.class, () -> bank.transactions.deposit("ghost", acct, new BigDecimal("5")));
        assertThrows(BankException.class, () -> bank.transactions.deposit(null, acct, new BigDecimal("5")));
        assertThrows(BankException.class, () -> bank.accounts.openAccount(" ", "C", "9876543210",
                AccountType.CURRENT, new BigDecimal("10")));
        assertEquals(1, bank.transactions.history(acct).size());
    }

    @Test
    void userChangesSurviveRestart() {
        createStaff("ravi");
        bank.auth.setActive(ADMIN, "ravi", false);
        TestBank restarted = bank.reopen();
        User ravi = restarted.userRepo.findByUsername("ravi").orElseThrow();
        assertFalse(ravi.active());
        assertFalse(ravi.admin());
        assertTrue(restarted.userRepo.findByUsername(ADMIN).orElseThrow().admin());
    }
}
