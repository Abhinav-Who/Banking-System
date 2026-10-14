package bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A customer account. Holds state only; every rule about what may happen to an
 * account lives in the service layer.
 *
 * <p>Balances are always stored with scale 2. {@link #setBalance} refuses a value
 * that would need rounding, so a bug elsewhere cannot silently lose fractions of money.
 */
public abstract class Account {

    private final String accountNumber;
    private final String customerName;
    private final String phone;
    private final LocalDateTime openedAt;
    private final String openedBy;
    private BigDecimal balance;
    private AccountStatus status;

    protected Account(String accountNumber, String customerName, String phone,
                      BigDecimal balance, AccountStatus status, LocalDateTime openedAt, String openedBy) {
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.customerName = Objects.requireNonNull(customerName, "customerName");
        this.phone = Objects.requireNonNull(phone, "phone");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.openedBy = openedBy == null || openedBy.isBlank() ? null : openedBy;
        setBalance(balance);
    }

    /**
     * Creates the right subclass for {@code type}.
     *
     * @param openedBy username of the staff member who opened it; null for accounts opened
     *                 before this was tracked
     */
    public static Account of(AccountType type, String accountNumber, String customerName, String phone,
                             BigDecimal balance, AccountStatus status, LocalDateTime openedAt, String openedBy) {
        return switch (Objects.requireNonNull(type, "type")) {
            case SAVINGS -> new SavingsAccount(accountNumber, customerName, phone, balance, status, openedAt, openedBy);
            case CURRENT -> new CurrentAccount(accountNumber, customerName, phone, balance, status, openedAt, openedBy);
        };
    }

    public abstract AccountType getType();

    /** An independent copy, so callers can never mutate stored state by accident. */
    public Account copy() {
        return of(getType(), accountNumber, customerName, phone, balance, status, openedAt, openedBy);
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    /** Null for accounts opened before this was tracked. */
    public String getOpenedBy() {
        return openedBy;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isClosed() {
        return status == AccountStatus.CLOSED;
    }

    public final void setBalance(BigDecimal balance) {
        // UNNECESSARY throws ArithmeticException if rounding would be needed.
        this.balance = Objects.requireNonNull(balance, "balance").setScale(2, RoundingMode.UNNECESSARY);
    }

    public final void setStatus(AccountStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    @Override
    public String toString() {
        return getType().label() + " " + accountNumber + " (" + customerName + ")";
    }
}
