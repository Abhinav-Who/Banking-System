package bank.service;

import bank.model.Account;
import bank.model.Transaction;
import bank.model.TransactionType;
import bank.repository.AccountRepository;
import bank.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The only place a balance changes. Applying a change and recording its transaction happen
 * together, so "every balance change has a transaction" holds by construction. Callers must
 * run inside a unit of work.
 */
final class Ledger {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    Ledger(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    Transaction post(Account account, TransactionType type, BigDecimal amount,
                     String referenceAccount, LocalDateTime when) {
        BigDecimal newBalance = type.isDebit()
                ? account.getBalance().subtract(amount)
                : account.getBalance().add(amount);
        account.setBalance(newBalance);
        accounts.save(account);
        Transaction tx = new Transaction(UUID.randomUUID().toString(), account.getAccountNumber(), when,
                type, amount, newBalance, referenceAccount);
        transactions.save(tx);
        return tx;
    }
}
