package bank.model;

/** Lifecycle state of an account. Closed accounts are kept on record but reject all transactions. */
public enum AccountStatus {
    ACTIVE,
    CLOSED
}
