package bank.service;

import bank.model.Account;
import bank.model.AccountType;
import bank.model.Transaction;
import bank.model.User;
import bank.repository.file.FileStorage;
import bank.repository.file.FileUnitOfWork;
import bank.repository.file.FileUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Data folders written by the single-user version must keep working. */
class LegacyDataTest {

    @TempDir
    Path dir;

    @Test
    void loadsFilesWrittenBeforeMultipleUsersExisted() throws Exception {
        // Create a valid hashed admin row, then strip it down to the old 5-column format.
        Path scratch = Files.createTempDirectory("hash");
        new TestBank(scratch).auth.changePassword(TestBank.ADMIN, "admin123".toCharArray(),
                "Old-admin-pw".toCharArray(), "Old-admin-pw".toCharArray());
        String fullRow = Files.readAllLines(scratch.resolve("users.csv")).get(1);
        String legacyUserRow = String.join(",", List.of(fullRow.split(",")).subList(0, 5));

        Files.writeString(dir.resolve("users.csv"),
                "# username,saltBase64,passwordHashBase64,iterations,mustChangePassword\n" + legacyUserRow + "\n");
        Files.writeString(dir.resolve("accounts.csv"),
                "# accountNumber,type,customerName,phone,balance,status,openedAt\n"
                        + "1234567890,SAVINGS,Old Customer,9876543210,1500.00,ACTIVE,2026-01-01T09:00\n");
        Files.writeString(dir.resolve("transactions.csv"),
                "# id,accountNumber,timestamp,type,amount,balanceAfter,referenceAccount\n"
                        + "t1,1234567890,2026-01-01T09:00,OPENING_DEPOSIT,1500.00,1500.00,\n");

        TestBank bank = new TestBank(dir);

        User admin = bank.auth.login("admin", "Old-admin-pw".toCharArray());
        assertTrue(admin.admin(), "the old single user becomes an administrator");
        assertTrue(admin.active());
        assertNull(admin.createdAt());
        Account old = bank.accounts.getAccount("1234567890");
        assertNull(old.getOpenedBy());
        assertNull(bank.transactions.history("1234567890").get(0).performedBy());

        // New activity is recorded, and the rewritten files load again.
        bank.transactions.deposit("admin", "1234567890", new BigDecimal("10"));
        bank.auth.createUser("admin", "ravi", "Temp-pass1".toCharArray(), "Temp-pass1".toCharArray(), false);
        TestBank restarted = bank.reopen();
        List<Transaction> history = restarted.transactions.history("1234567890");
        assertEquals(2, history.size());
        assertNull(history.get(0).performedBy());
        assertEquals("admin", history.get(1).performedBy());
        assertEquals(new BigDecimal("1510.00"), restarted.accounts.getAccount("1234567890").getBalance());
        assertEquals(AccountType.SAVINGS, restarted.accounts.getAccount("1234567890").getType());
        assertEquals(2, restarted.userRepo.count());
        assertTrue(Files.readAllLines(dir.resolve("users.csv")).get(0).endsWith("createdAt"));
    }

    @Test
    void rejectsGarbageInBooleanColumns() throws Exception {
        Files.writeString(dir.resolve("users.csv"), "# h\nadmin,c2FsdA==,aGFzaA==,1000,false,yes,true,\n");
        FileStorage storage = new FileStorage(dir);
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(bank.repository.PersistenceException.class,
                () -> new FileUserRepository(storage, new FileUnitOfWork(storage)));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }
}
