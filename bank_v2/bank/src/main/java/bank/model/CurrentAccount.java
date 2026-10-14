package bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CurrentAccount extends Account {

    public CurrentAccount(String accountNumber, String customerName, String phone,
                          BigDecimal balance, AccountStatus status, LocalDateTime openedAt, String openedBy) {
        super(accountNumber, customerName, phone, balance, status, openedAt, openedBy);
    }

    @Override
    public AccountType getType() {
        return AccountType.CURRENT;
    }
}
