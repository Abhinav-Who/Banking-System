package bank.service;

import bank.model.Account;
import bank.model.AccountStatus;
import bank.model.AccountType;
import bank.model.TransactionType;
import bank.repository.AccountRepository;
import bank.repository.TransactionRepository;
import bank.repository.UnitOfWork;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Opening, finding, searching and closing accounts. */
public final class AccountService {

    private static final long FIRST_ACCOUNT_NUMBER = 1_000_000_000L; // 10 digits, no leading zero
    private static final long ACCOUNT_NUMBER_RANGE = 9_000_000_000L;
    private static final int MAX_NUMBER_ATTEMPTS = 1_000;

    private final AccountRepository accounts;
    private final UnitOfWork unitOfWork;
    private final Ledger ledger;
    private final Clock clock;
    private final RandomGenerator random;

    public AccountService(AccountRepository accounts, TransactionRepository transactions,
                          UnitOfWork unitOfWork, Clock clock) {
        this(accounts, transactions, unitOfWork, clock, new SecureRandom());
    }

    AccountService(AccountRepository accounts, TransactionRepository transactions,
                   UnitOfWork unitOfWork, Clock clock, RandomGenerator random) {
        this.accounts = Objects.requireNonNull(accounts);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.ledger = new Ledger(accounts, Objects.requireNonNull(transactions));
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Opens an account and records the opening deposit as its first transaction.
     * Savings accounts need an opening deposit of at least the minimum balance.
     */
    public Account openAccount(String customerName, String phone, AccountType type, BigDecimal openingDeposit) {
        String name = BankRules.validateCustomerName(customerName);
        String cleanPhone = BankRules.validatePhone(phone);
        if (type == null) {
            throw new BankException("Please choose an account type.");
        }
        BigDecimal deposit = BankRules.validateAmount(openingDeposit);
        if (type == AccountType.SAVINGS && deposit.compareTo(BankRules.SAVINGS_MINIMUM_BALANCE) < 0) {
            throw new BankException("A Savings account needs an opening deposit of at least "
                    + BankRules.formatMoney(BankRules.SAVINGS_MINIMUM_BALANCE) + ".");
        }
        return unitOfWork.execute(() -> {
            LocalDateTime now = now();
            Account account = Account.of(type, newAccountNumber(), name, cleanPhone,
                    BigDecimal.ZERO, AccountStatus.ACTIVE, now);
            ledger.post(account, TransactionType.OPENING_DEPOSIT, deposit, null, now);
            return account.copy();
        });
    }

    /** Any account, active or closed. */
    public Account getAccount(String accountNumber) {
        String number = BankRules.validateAccountNumber(accountNumber);
        return accounts.findByNumber(number)
                .orElseThrow(() -> new BankException("No account found with number " + number + "."));
    }

    public List<Account> listAccounts() {
        return accounts.findAll();
    }

    /**
     * Case-insensitive match on customer name, or a match anywhere in the account number.
     * A blank query returns every account.
     */
    public List<Account> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return listAccounts();
        }
        return accounts.findAll().stream()
                .filter(a -> a.getAccountNumber().contains(q)
                        || a.getCustomerName().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    /** Closes an account whose balance is exactly zero. */
    public void closeAccount(String accountNumber) {
        unitOfWork.run(() -> {
            Account account = requireOpen(accountNumber);
            int sign = account.getBalance().signum();
            if (sign > 0) {
                throw new BankException("Account " + account.getAccountNumber() + " still holds "
                        + BankRules.formatMoney(account.getBalance())
                        + ". The balance must be zero before the account can be closed.");
            }
            if (sign < 0) {
                throw overdrawnCannotClose(account);
            }
            account.setStatus(AccountStatus.CLOSED);
            accounts.save(account);
        });
    }

    /**
     * Pays out the whole remaining balance (recorded as a CLOSING_PAYOUT transaction) and then
     * closes the account, as one atomic step. This exists because a Savings account can never
     * reach zero through ordinary withdrawals (they must leave 1,000.00), so without it a
     * Savings account could never be closed. The account is at zero at the moment it closes.
     *
     * @return the amount paid out (zero if the balance already was zero)
     */
    public BigDecimal payOutAndClose(String accountNumber) {
        return unitOfWork.execute(() -> {
            Account account = requireOpen(accountNumber);
            BigDecimal payout = account.getBalance();
            if (payout.signum() < 0) {
                throw overdrawnCannotClose(account);
            }
            if (payout.signum() > 0) {
                ledger.post(account, TransactionType.CLOSING_PAYOUT, payout, null, now());
            }
            account.setStatus(AccountStatus.CLOSED);
            accounts.save(account);
            return payout;
        });
    }

    private BankException overdrawnCannotClose(Account account) {
        return new BankException("Account " + account.getAccountNumber() + " is overdrawn by "
                + BankRules.formatMoney(account.getBalance().negate())
                + ". Deposit enough to bring it to zero before closing.");
    }

    private Account requireOpen(String accountNumber) {
        Account account = getAccount(accountNumber);
        if (account.isClosed()) {
            throw new BankException("Account " + account.getAccountNumber() + " is already closed.");
        }
        return account;
    }

    private String newAccountNumber() {
        for (int i = 0; i < MAX_NUMBER_ATTEMPTS; i++) {
            String candidate = Long.toString(FIRST_ACCOUNT_NUMBER + random.nextLong(ACCOUNT_NUMBER_RANGE));
            if (!accounts.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
