package bank.repository;

import bank.model.Transaction;

import java.util.List;

/** Append-only store of transactions. */
public interface TransactionRepository {

    void save(Transaction transaction);

    /** All transactions of one account, oldest first. */
    List<Transaction> findByAccount(String accountNumber);
}
