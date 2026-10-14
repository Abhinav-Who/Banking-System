package bank.model;

/** Kind of balance change. {@code debit} says whether money left the account. */
public enum TransactionType {
    OPENING_DEPOSIT("Opening deposit", false),
    DEPOSIT("Deposit", false),
    WITHDRAWAL("Withdrawal", true),
    TRANSFER_OUT("Transfer out", true),
    TRANSFER_IN("Transfer in", false),
    CLOSING_PAYOUT("Closing payout", true);

    private final String label;
    private final boolean debit;

    TransactionType(String label, boolean debit) {
        this.label = label;
        this.debit = debit;
    }

    public String label() {
        return label;
    }

    public boolean isDebit() {
        return debit;
    }

    @Override
    public String toString() {
        return label;
    }
}
