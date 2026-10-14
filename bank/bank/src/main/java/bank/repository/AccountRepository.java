package bank.repository;

import bank.model.Account;

import java.util.List;
import java.util.Optional;

/** Stores accounts. Returned objects are copies; call {@link #save} to persist changes. */
public interface AccountRepository {

    Optional<Account> findByNumber(String accountNumber);

    List<Account> findAll();

    boolean exists(String accountNumber);

    /** Inserts or replaces the account with the same number. */
    void save(Account account);
}
