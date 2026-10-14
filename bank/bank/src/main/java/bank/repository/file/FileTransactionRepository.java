package bank.repository.file;

import bank.model.Transaction;
import bank.model.TransactionType;
import bank.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Transactions in {@code transactions.csv}, in the order they happened. */
public final class FileTransactionRepository extends FileBackedRepository implements TransactionRepository {

    private final List<Transaction> transactions = new ArrayList<>();

    public FileTransactionRepository(FileStorage storage, FileUnitOfWork unitOfWork) {
        super(unitOfWork, "transactions.csv");
        loadFrom(storage);
    }

    @Override
    public void save(Transaction transaction) {
        unitOfWork.run(() -> {
            synchronized (this) {
                transactions.add(transaction);
                markDirty();
            }
        });
    }

    @Override
    public synchronized List<Transaction> findByAccount(String accountNumber) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.accountNumber().equals(accountNumber)) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    String header() {
        return "# id,accountNumber,timestamp,type,amount,balanceAfter,referenceAccount";
    }

    @Override
    synchronized List<List<String>> rows() {
        List<List<String>> rows = new ArrayList<>(transactions.size());
        for (Transaction t : transactions) {
            rows.add(List.of(t.id(), t.accountNumber(), t.timestamp().toString(), t.type().name(),
                    t.amount().toPlainString(), t.balanceAfter().toPlainString(),
                    t.referenceAccount() == null ? "" : t.referenceAccount()));
        }
        return rows;
    }

    @Override
    synchronized void clear() {
        transactions.clear();
    }

    @Override
    synchronized void addRow(List<String> f) {
        requireFieldCount(f, 7);
        transactions.add(new Transaction(f.get(0), f.get(1), LocalDateTime.parse(f.get(2)),
                TransactionType.valueOf(f.get(3)), new BigDecimal(f.get(4)), new BigDecimal(f.get(5)),
                f.get(6).isEmpty() ? null : f.get(6)));
    }
}
