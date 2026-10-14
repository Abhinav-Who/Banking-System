package bank.service;

import bank.model.Account;
import bank.model.AccountStatus;
import bank.model.AccountType;
import bank.model.Transaction;
import bank.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    @TempDir
    Path dir;

    private TestBank bank;

    @BeforeEach
    void setUp() {
        bank = new TestBank(dir);
    }

    private static BigDecimal money(String s) {
        return new BigDecimal(s);
    }

    @Test
    void opensAccountWithTenDigitNumberAndRecordsOpeningDeposit() {
        Account a = bank.accounts.openAccount(TestBank.ADMIN, "Asha Rao", "9876543210", AccountType.SAVINGS, money("2500"));

        assertTrue(a.getAccountNumber().matches("[1-9]\\d{9}"), a.getAccountNumber());
        assertEquals(money("2500.00"), a.getBalance());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());
        List<Transaction> history = bank.transactions.history(a.getAccountNumber());
        assertEquals(1, history.size());
        assertEquals(TransactionType.OPENING_DEPOSIT, history.get(0).type());
        assertEquals(money("2500.00"), history.get(0).balanceAfter());
    }

    @Test
    void savingsNeedsOpeningDepositOfAtLeastMinimumBalance() {
        assertThrows(BankException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.SAVINGS, money("999.99")));
        assertEquals(0, bank.accounts.listAccounts().size());
        bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.SAVINGS, money("1000.00"));
        assertEquals(1, bank.accounts.listAccounts().size());
    }

    @Test
    void rejectsInvalidCustomerDetails() {
        assertThrows(BankException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, " ", "9876543210", AccountType.CURRENT, money("10")));
        assertThrows(BankException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, "A", "12", AccountType.CURRENT, money("10")));
        assertThrows(BankException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", null, money("10")));
        assertThrows(BankException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.CURRENT, money("0")));
    }

    @Test
    void accountNumbersAreUniqueEvenWhenTheGeneratorRepeats() {
        // Generator that returns the same value twice, then a new one.
        RandomGenerator repeating = new RandomGenerator() {
            private final long[] values = {42, 42, 42, 7};
            private int i;

            @Override
            public long nextLong() {
                return values[Math.min(i++, values.length - 1)];
            }

            @Override
            public long nextLong(long bound) {
                return nextLong();
            }
        };
        TestBank b = new TestBank(dir.resolve("unique"), repeating);
        Account first = b.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.CURRENT, money("10"));
        Account second = b.accounts.openAccount(TestBank.ADMIN, "B", "9876543210", AccountType.CURRENT, money("10"));
        assertEquals("1000000042", first.getAccountNumber());
        assertEquals("1000000007", second.getAccountNumber());
        assertNotEquals(first.getAccountNumber(), second.getAccountNumber());
    }

    @Test
    void searchMatchesNameCaseInsensitivelyOrAccountNumber() {
        Account asha = bank.accounts.openAccount(TestBank.ADMIN, "Asha Rao", "9876543210", AccountType.SAVINGS, money("2000"));
        bank.accounts.openAccount(TestBank.ADMIN, "Vikram Shah", "9876543211", AccountType.CURRENT, money("50"));

        assertEquals(1, bank.accounts.search("ASHA").size());
        assertEquals(1, bank.accounts.search("vik").size());
        assertEquals(asha.getAccountNumber(), bank.accounts.search(asha.getAccountNumber()).get(0).getAccountNumber());
        assertEquals(2, bank.accounts.search("").size());
        assertEquals(0, bank.accounts.search("nobody").size());
    }

    @Test
    void closeRequiresZeroBalanceAndKeepsAccountOnRecord() {
        Account a = bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.CURRENT, money("300"));
        String n = a.getAccountNumber();

        assertThrows(BankException.class, () -> bank.accounts.closeAccount(TestBank.ADMIN, n));
        bank.transactions.withdraw(TestBank.ADMIN, n, money("300"));
        bank.accounts.closeAccount(TestBank.ADMIN, n);

        Account closed = bank.accounts.getAccount(n);
        assertEquals(AccountStatus.CLOSED, closed.getStatus());
        assertEquals(1, bank.accounts.listAccounts().size());
        assertThrows(BankException.class, () -> bank.accounts.closeAccount(TestBank.ADMIN, n)); // already closed
        assertEquals(2, bank.transactions.history(n).size()); // history still readable
    }

    @Test
    void overdrawnAccountCannotBeClosed() {
        Account a = bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.CURRENT, money("100"));
        bank.transactions.withdraw(TestBank.ADMIN, a.getAccountNumber(), money("600"));
        assertThrows(BankException.class, () -> bank.accounts.closeAccount(TestBank.ADMIN, a.getAccountNumber()));
        assertThrows(BankException.class, () -> bank.accounts.payOutAndClose(TestBank.ADMIN, a.getAccountNumber()));
        assertEquals(AccountStatus.ACTIVE, bank.accounts.getAccount(a.getAccountNumber()).getStatus());
    }

    @Test
    void savingsCanBeClosedViaPayoutWhichRecordsATransaction() {
        Account a = bank.accounts.openAccount(TestBank.ADMIN, "A", "9876543210", AccountType.SAVINGS, money("1500.25"));
        String n = a.getAccountNumber();

        BigDecimal payout = bank.accounts.payOutAndClose(TestBank.ADMIN, n);

        assertEquals(money("1500.25"), payout);
        Account closed = bank.accounts.getAccount(n);
        assertEquals(AccountStatus.CLOSED, closed.getStatus());
        assertEquals(money("0.00"), closed.getBalance());
        List<Transaction> history = bank.transactions.history(n);
        Transaction last = history.get(history.size() - 1);
        assertEquals(TransactionType.CLOSING_PAYOUT, last.type());
        assertEquals(money("0.00"), last.balanceAfter());
    }

    @Test
    void unknownAccountGivesClearError() {
        BankException e = assertThrows(BankException.class, () -> bank.accounts.getAccount("1234567890"));
        assertTrue(e.getMessage().contains("1234567890"));
    }
}
