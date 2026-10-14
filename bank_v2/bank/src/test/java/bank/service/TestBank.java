package bank.service;

import bank.repository.PersistenceException;
import bank.repository.file.FileAccountRepository;
import bank.repository.file.FileStorage;
import bank.repository.file.FileTransactionRepository;
import bank.repository.file.FileUnitOfWork;
import bank.repository.file.FileUserRepository;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Fully wired system over a temp folder, with a storage whose writes can be made to fail. */
final class TestBank {

    /** The built-in administrator, used as the acting user in banking tests. */
    static final String ADMIN = "admin";

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:30:00Z"), ZoneOffset.UTC);

    /** Storage that can simulate a disk failure on the next write. */
    static final class FlakyStorage extends FileStorage {
        boolean failWrites;

        FlakyStorage(Path dir) {
            super(dir);
        }

        @Override
        public synchronized void write(Map<String, List<String>> files) {
            if (failWrites) {
                throw new PersistenceException("simulated disk failure");
            }
            super.write(files);
        }
    }

    final Path dir;
    final FlakyStorage storage;
    final FileAccountRepository accountRepo;
    final FileTransactionRepository transactionRepo;
    final FileUserRepository userRepo;
    final AccountService accounts;
    final TransactionService transactions;
    final AuthService auth;

    TestBank(Path dir) {
        this(dir, null);
    }

    TestBank(Path dir, RandomGenerator random) {
        this.dir = dir;
        storage = new FlakyStorage(dir);
        FileUnitOfWork uow = new FileUnitOfWork(storage);
        accountRepo = new FileAccountRepository(storage, uow);
        transactionRepo = new FileTransactionRepository(storage, uow);
        userRepo = new FileUserRepository(storage, uow);
        accounts = random == null
                ? new AccountService(accountRepo, transactionRepo, userRepo, uow, CLOCK)
                : new AccountService(accountRepo, transactionRepo, userRepo, uow, CLOCK, random);
        transactions = new TransactionService(accountRepo, transactionRepo, userRepo, uow, CLOCK);
        auth = new AuthService(userRepo, new PasswordHasher(1_000), CLOCK); // low iterations keep tests fast
        auth.ensureDefaultAdmin();
    }

    /** Simulates an application restart over the same data folder. */
    TestBank reopen() {
        return new TestBank(dir);
    }
}
