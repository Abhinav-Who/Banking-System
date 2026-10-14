package bank.service;

import bank.model.Account;
import bank.model.AccountType;
import bank.model.Transaction;
import bank.model.TransactionType;
import bank.repository.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionServiceTest {

    @TempDir
    Path dir;

    private TestBank bank;
    private String savings;   // opened with 5,000.00
    private String current;   // opened with 1,000.00

    @BeforeEach
    void setUp() {
        bank = new TestBank(dir);
        savings = bank.accounts.openAccount(TestBank.ADMIN, "Saver", "9876543210", AccountType.SAVINGS, money("5000")).getAccountNumber();
        current = bank.accounts.openAccount(TestBank.ADMIN, "Spender", "9876543211", AccountType.CURRENT, money("1000")).getAccountNumber();
    }

    private static BigDecimal money(String s) {
        return new BigDecimal(s);
    }

    private BigDecimal balance(String n) {
        return bank.accounts.getAccount(n).getBalance();
    }

    private int txCount(String n) {
        return bank.transactions.history(n).size();
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        Transaction tx = bank.transactions.deposit(TestBank.ADMIN, savings, money("250.75"));
        assertEquals(money("5250.75"), balance(savings));
        assertEquals(TransactionType.DEPOSIT, tx.type());
        assertEquals(money("5250.75"), tx.balanceAfter());
        assertEquals(2, txCount(savings));
    }

    @Test
    void savingsMayWithdrawDownToExactlyTheMinimum() {
        bank.transactions.withdraw(TestBank.ADMIN, savings, money("4000"));
        assertEquals(money("1000.00"), balance(savings));
    }

    @Test
    void savingsWithdrawalBreachingMinimumIsRejectedAndChangesNothing() {
        assertThrows(BankException.class, () -> bank.transactions.withdraw(TestBank.ADMIN, savings, money("4000.01")));
        assertEquals(money("5000.00"), balance(savings));
        assertEquals(1, txCount(savings));
    }

    @Test
    void currentMayGoToExactlyTheOverdraftLimitButNotBelow() {
        bank.transactions.withdraw(TestBank.ADMIN, current, money("6000"));
        assertEquals(money("-5000.00"), balance(current));
        assertThrows(BankException.class, () -> bank.transactions.withdraw(TestBank.ADMIN, current, money("0.01")));
        assertEquals(money("-5000.00"), balance(current));
    }

    @Test
    void rejectsBadAmounts() {
        assertThrows(BankException.class, () -> bank.transactions.deposit(TestBank.ADMIN, savings, money("0")));
        assertThrows(BankException.class, () -> bank.transactions.deposit(TestBank.ADMIN, savings, money("-1")));
        assertThrows(BankException.class, () -> bank.transactions.deposit(TestBank.ADMIN, savings, money("1.001")));
        assertEquals(1, txCount(savings));
    }

    @Test
    void transferMovesMoneyAndRecordsBothSides() {
        bank.transactions.transfer(TestBank.ADMIN, savings, current, money("1500.50"));

        assertEquals(money("3499.50"), balance(savings));
        assertEquals(money("2500.50"), balance(current));
        Transaction out = last(savings);
        Transaction in = last(current);
        assertEquals(TransactionType.TRANSFER_OUT, out.type());
        assertEquals(current, out.referenceAccount());
        assertEquals(TransactionType.TRANSFER_IN, in.type());
        assertEquals(savings, in.referenceAccount());
        assertEquals(out.timestamp(), in.timestamp());
    }

    @Test
    void transferRespectsSourceLimits() {
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, savings, current, money("4000.01")));
        bank.transactions.transfer(TestBank.ADMIN, current, savings, money("6000")); // current to -5000
        assertEquals(money("-5000.00"), balance(current));
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, current, savings, money("1")));
    }

    @Test
    void cannotTransferToSameNonExistentOrClosedAccount() {
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, savings, savings, money("10")));
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, savings, "1234567890", money("10")));

        String closed = bank.accounts.openAccount(TestBank.ADMIN, "Gone", "9876543212", AccountType.CURRENT, money("10")).getAccountNumber();
        bank.transactions.withdraw(TestBank.ADMIN, closed, money("10"));
        bank.accounts.closeAccount(TestBank.ADMIN, closed);
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, savings, closed, money("10")));
        assertThrows(BankException.class, () -> bank.transactions.transfer(TestBank.ADMIN, closed, savings, money("10")));

        assertEquals(money("5000.00"), balance(savings));
        assertEquals(1, txCount(savings));
    }

    @Test
    void closedAccountRejectsAllTransactions() {
        String n = bank.accounts.openAccount(TestBank.ADMIN, "Gone", "9876543212", AccountType.SAVINGS, money("1000")).getAccountNumber();
        bank.accounts.payOutAndClose(TestBank.ADMIN, n);
        assertThrows(BankException.class, () -> bank.transactions.deposit(TestBank.ADMIN, n, money("10")));
        assertThrows(BankException.class, () -> bank.transactions.withdraw(TestBank.ADMIN, n, money("10")));
    }

    @Test
    void transferIsAtomicWhenSavingFails() throws Exception {
        String accountsBefore = Files.readString(dir.resolve("accounts.csv"));
        String transactionsBefore = Files.readString(dir.resolve("transactions.csv"));

        bank.storage.failWrites = true;
        assertThrows(PersistenceException.class, () -> bank.transactions.transfer(TestBank.ADMIN, savings, current, money("100")));
        bank.storage.failWrites = false;

        // Memory rolled back...
        assertEquals(money("5000.00"), balance(savings));
        assertEquals(money("1000.00"), balance(current));
        assertEquals(1, txCount(savings));
        assertEquals(1, txCount(current));
        // ...disk untouched...
        assertEquals(accountsBefore, Files.readString(dir.resolve("accounts.csv")));
        assertEquals(transactionsBefore, Files.readString(dir.resolve("transactions.csv")));
        // ...and the system still works afterwards.
        bank.transactions.transfer(TestBank.ADMIN, savings, current, money("100"));
        TestBank restarted = bank.reopen();
        assertEquals(money("4900.00"), restarted.accounts.getAccount(savings).getBalance());
        assertEquals(money("1100.00"), restarted.accounts.getAccount(current).getBalance());
    }

    @Test
    void failedAccountOpeningLeavesNoTrace() {
        int before = bank.accounts.listAccounts().size();
        bank.storage.failWrites = true;
        assertThrows(PersistenceException.class,
                () -> bank.accounts.openAccount(TestBank.ADMIN, "X", "9876543213", AccountType.CURRENT, money("10")));
        bank.storage.failWrites = false;
        assertEquals(before, bank.accounts.listAccounts().size());
    }

    @Test
    void everyBalanceEqualsTheSumOfItsTransactions() {
        bank.transactions.deposit(TestBank.ADMIN, savings, money("123.45"));
        bank.transactions.withdraw(TestBank.ADMIN, current, money("4000"));
        bank.transactions.transfer(TestBank.ADMIN, savings, current, money("77.70"));
        bank.transactions.transfer(TestBank.ADMIN, current, savings, money("0.05"));

        for (Account a : bank.accounts.listAccounts()) {
            BigDecimal sum = bank.transactions.history(a.getAccountNumber()).stream()
                    .map(Transaction::signedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sum.compareTo(a.getBalance()), a.getAccountNumber());
            assertEquals(2, a.getBalance().scale());
        }
    }

    @Test
    void dataSurvivesRestart() {
        bank.transactions.transfer(TestBank.ADMIN, savings, current, money("250"));
        TestBank restarted = bank.reopen();

        assertEquals(money("4750.00"), restarted.accounts.getAccount(savings).getBalance());
        assertEquals(money("1250.00"), restarted.accounts.getAccount(current).getBalance());
        assertEquals(2, restarted.transactions.history(savings).size());
        assertEquals(AccountType.SAVINGS, restarted.accounts.getAccount(savings).getType());
    }

    @Test
    void awkwardCustomerNamesRoundTripThroughCsv() {
        String n = bank.accounts.openAccount(TestBank.ADMIN, "O'Brien, \"Pat\" #1", "9876543214", AccountType.CURRENT, money("1"))
                .getAccountNumber();
        TestBank restarted = bank.reopen();
        assertEquals("O'Brien, \"Pat\" #1", restarted.accounts.getAccount(n).getCustomerName());
    }

    @Test
    void returnedAccountsAreCopies() {
        Account a = bank.accounts.getAccount(savings);
        a.setBalance(money("999999.00"));
        assertEquals(money("5000.00"), balance(savings));
        assertTrue(bank.accounts.listAccounts().stream().noneMatch(x -> x.getBalance().equals(money("999999.00"))));
    }

    private Transaction last(String n) {
        List<Transaction> h = bank.transactions.history(n);
        return h.get(h.size() - 1);
    }
}
