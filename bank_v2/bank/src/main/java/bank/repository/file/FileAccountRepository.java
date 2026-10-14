package bank.repository.file;

import bank.model.Account;
import bank.model.AccountStatus;
import bank.model.AccountType;
import bank.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Accounts in {@code accounts.csv}. */
public final class FileAccountRepository extends FileBackedRepository implements AccountRepository {

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public FileAccountRepository(FileStorage storage, FileUnitOfWork unitOfWork) {
        super(unitOfWork, "accounts.csv");
        loadFrom(storage);
    }

    @Override
    public synchronized Optional<Account> findByNumber(String accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber)).map(Account::copy);
    }

    @Override
    public synchronized List<Account> findAll() {
        List<Account> result = new ArrayList<>(accounts.size());
        accounts.values().forEach(a -> result.add(a.copy()));
        return result;
    }

    @Override
    public synchronized boolean exists(String accountNumber) {
        return accounts.containsKey(accountNumber);
    }

    @Override
    public void save(Account account) {
        Account stored = account.copy();
        unitOfWork.run(() -> {
            synchronized (this) {
                accounts.put(stored.getAccountNumber(), stored);
                markDirty();
            }
        });
    }

    @Override
    String header() {
        return "# accountNumber,type,customerName,phone,balance,status,openedAt,openedBy";
    }

    @Override
    synchronized List<List<String>> rows() {
        List<List<String>> rows = new ArrayList<>(accounts.size());
        for (Account a : accounts.values()) {
            rows.add(List.of(a.getAccountNumber(), a.getType().name(), a.getCustomerName(), a.getPhone(),
                    a.getBalance().toPlainString(), a.getStatus().name(), a.getOpenedAt().toString(),
                    a.getOpenedBy() == null ? "" : a.getOpenedBy()));
        }
        return rows;
    }

    @Override
    synchronized void clear() {
        accounts.clear();
    }

    @Override
    synchronized void addRow(List<String> f) {
        requireFieldCount(f, 7, 8); // 7 = written before openedBy existed
        Account a = Account.of(AccountType.valueOf(f.get(1)), f.get(0), f.get(2), f.get(3),
                new BigDecimal(f.get(4)), AccountStatus.valueOf(f.get(5)), LocalDateTime.parse(f.get(6)),
                optional(f, 7));
        if (accounts.putIfAbsent(a.getAccountNumber(), a) != null) {
            throw new IllegalArgumentException("duplicate account number " + a.getAccountNumber());
        }
    }
}
