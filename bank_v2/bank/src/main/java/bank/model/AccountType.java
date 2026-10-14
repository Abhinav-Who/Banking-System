package bank.model;

/** The two kinds of account the bank offers. */
public enum AccountType {
    SAVINGS("Savings"),
    CURRENT("Current");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
