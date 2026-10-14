package bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SavingsAccount extends Account {

    public SavingsAccount(String accountNumber, String customerName, String phone,
                          BigDecimal balance, AccountStatus status, LocalDateTime openedAt) {
        super(accountNumber, customerName, phone, balance, status, openedAt);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }
}
