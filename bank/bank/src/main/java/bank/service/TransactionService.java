package bank.service;

import bank.model.Account;
import bank.model.Transaction;
import bank.model.TransactionType;
import bank.repository.AccountRepository;
import bank.repository.TransactionRepository;
import bank.repository.UnitOfWork;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Deposits, withdrawals, transfers and transaction history. */
public final class TransactionService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final UnitOfWork unitOfWork;
    private final Ledger ledger;
    private final Clock clock;

    public TransactionService(AccountRepository accounts, TransactionRepository transactions,
                              UnitOfWork unitOfWork, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.transactions = Objects.requireNonNull(transactions);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.ledger = new Ledger(accounts, transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        BigDecimal value = BankRules.validateAmount(amount);
        return unitOfWork.execute(() -> {
            Account account = requireActive(accountNumber);
            return ledger.post(account, TransactionType.DEPOSIT, value, null, now());
        });
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount) {
        BigDecimal value = BankRules.validateAmount(amount);
        return unitOfWork.execute(() -> {
            Account account = requireActive(accountNumber);
            BankRules.checkDebitAllowed(account, value);
            return ledger.post(account, TransactionType.WITHDRAWAL, value, null, now());
        });
    }

    /**
     * Moves money between two accounts atomically: both balances and both transaction records
     * are saved together, or nothing changes.
     *
     * @return the debit record on the source account
     */
    public Transaction transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        String from = BankRules.validateAccountNumber(fromAccountNumber);
        String to = BankRules.validateAccountNumber(toAccountNumber);
        if (from.equals(to)) {
            throw new BankException("Cannot transfer to the same account.");
        }
        BigDecimal value = BankRules.validateAmount(amount);
        return unitOfWork.execute(() -> {
            Account source = requireActive(from);
            Account target = requireActive(to);
            BankRules.checkDebitAllowed(source, value);
            LocalDateTime when = now();
            Transaction debit = ledger.post(source, TransactionType.TRANSFER_OUT, value, to, when);
            ledger.post(target, TransactionType.TRANSFER_IN, value, from, when);
            return debit;
        });
    }

    /** Full history, oldest first. Works for closed accounts too. */
    public List<Transaction> history(String accountNumber) {
        String number = BankRules.validateAccountNumber(accountNumber);
        if (!accounts.exists(number)) {
            throw new BankException("No account found with number " + number + ".");
        }
        return transactions.findByAccount(number);
    }

    private Account requireActive(String accountNumber) {
        String number = BankRules.validateAccountNumber(accountNumber);
        Account account = accounts.findByNumber(number)
                .orElseThrow(() -> new BankException("Account " + number + " does not exist."));
        if (account.isClosed()) {
            throw new BankException("Account " + number + " is closed and cannot accept transactions.");
        }
        return account;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
