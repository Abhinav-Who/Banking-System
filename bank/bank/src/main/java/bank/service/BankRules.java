package bank.service;

import bank.model.Account;
import bank.model.AccountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Every validation rule and limit in one place. The UI may call these to check input before
 * submitting, but the services always re-check, so the UI is never the line of defence.
 */
public final class BankRules {

    public static final BigDecimal SAVINGS_MINIMUM_BALANCE = new BigDecimal("1000.00");
    public static final BigDecimal CURRENT_OVERDRAFT_LIMIT = new BigDecimal("5000.00");
    /** Sanity cap on a single amount, to catch typos with extra digits. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");

    public static final int MAX_NAME_LENGTH = 100;

    private static final Pattern AMOUNT_TEXT = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{10}");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{7,15}");

    private BankRules() {
    }

    // ---- amounts -------------------------------------------------------------------------

    /** Parses user-typed text such as {@code 1500} or {@code 1500.50} into a validated amount. */
    public static BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new BankException("Please enter an amount.");
        }
        String t = text.trim();
        if (!AMOUNT_TEXT.matcher(t).matches()) {
            throw new BankException("Amount must be a positive number such as 1500 or 1500.50 "
                    + "(digits only, no commas or currency symbols).");
        }
        return validateAmount(new BigDecimal(t));
    }

    /** Checks amount > 0, at most 2 decimal places, within the sanity cap; returns it at scale 2. */
    public static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BankException("Please enter an amount.");
        }
        if (amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BankException("Amount can have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BankException("Amount is too large (maximum " + formatMoney(MAX_AMOUNT) + ").");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    // ---- balances ------------------------------------------------------------------------

    /** Lowest balance an account of this type may hold: 1000.00 for Savings, -5000.00 for Current. */
    public static BigDecimal minimumBalance(AccountType type) {
        return switch (type) {
            case SAVINGS -> SAVINGS_MINIMUM_BALANCE;
            case CURRENT -> CURRENT_OVERDRAFT_LIMIT.negate();
        };
    }

    /** How much can still be taken out without breaking the minimum balance or overdraft limit. */
    public static BigDecimal availableToWithdraw(Account account) {
        BigDecimal available = account.getBalance().subtract(minimumBalance(account.getType()));
        return available.signum() > 0 ? available : BigDecimal.ZERO.setScale(2);
    }

    /** Rejects a debit that would push the account below its allowed minimum. */
    static void checkDebitAllowed(Account account, BigDecimal amount) {
        BigDecimal after = account.getBalance().subtract(amount);
        if (after.compareTo(minimumBalance(account.getType())) >= 0) {
            return;
        }
        String available = formatMoney(availableToWithdraw(account));
        if (account.getType() == AccountType.SAVINGS) {
            throw new BankException("Insufficient funds in account " + account.getAccountNumber()
                    + ". A Savings account must keep a minimum balance of " + formatMoney(SAVINGS_MINIMUM_BALANCE)
                    + ".\nThe most you can take out now is " + available + ".");
        }
        throw new BankException("Overdraft limit exceeded for account " + account.getAccountNumber()
                + ". A Current account cannot go below " + formatMoney(CURRENT_OVERDRAFT_LIMIT.negate())
                + ".\nThe most you can take out now is " + available + ".");
    }

    // ---- identifiers and customer details ------------------------------------------------

    public static String validateAccountNumber(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new BankException("Please enter an account number.");
        }
        if (!ACCOUNT_NUMBER.matcher(t).matches()) {
            throw new BankException("Account number must be exactly 10 digits.");
        }
        return t;
    }

    public static String validateCustomerName(String text) {
        String t = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            throw new BankException("Please enter the customer's name.");
        }
        if (t.length() > MAX_NAME_LENGTH) {
            throw new BankException("Customer name can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (t.chars().anyMatch(Character::isISOControl)) {
            throw new BankException("Customer name contains invalid characters.");
        }
        return t;
    }

    /** Accepts digits with an optional leading '+'; spaces and hyphens are ignored. */
    public static String validatePhone(String text) {
        String t = text == null ? "" : text.trim().replaceAll("[\\s-]", "");
        if (t.isEmpty()) {
            throw new BankException("Please enter a phone number.");
        }
        if (!PHONE.matcher(t).matches()) {
            throw new BankException("Phone number must be 7 to 15 digits, optionally starting with '+'.");
        }
        return t;
    }

    // ---- formatting ----------------------------------------------------------------------

    /** e.g. {@code 12,345.60} or {@code -4,000.00}. Fixed locale so output never varies by machine. */
    public static String formatMoney(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }
}
