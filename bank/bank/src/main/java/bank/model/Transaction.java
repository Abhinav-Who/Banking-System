package bank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An immutable record of one successful balance change on one account.
 * A transfer produces two of these (TRANSFER_OUT and TRANSFER_IN) that point at each other
 * through {@code referenceAccount}.
 *
 * @param amount           always positive; {@link TransactionType#isDebit()} gives the direction
 * @param referenceAccount the other account for transfers, otherwise {@code null}
 */
public record Transaction(String id,
                          String accountNumber,
                          LocalDateTime timestamp,
                          TransactionType type,
                          BigDecimal amount,
                          BigDecimal balanceAfter,
                          String referenceAccount) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        amount = Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.UNNECESSARY);
        balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter").setScale(2, RoundingMode.UNNECESSARY);
        if (referenceAccount != null && referenceAccount.isBlank()) {
            referenceAccount = null;
        }
    }

    /** Amount with sign: negative for money leaving the account. */
    public BigDecimal signedAmount() {
        return type.isDebit() ? amount.negate() : amount;
    }
}
