package bank.service;

/**
 * A business rule or validation failure. The message is written for the end user and is
 * shown as-is in an error dialog.
 */
public class BankException extends RuntimeException {

    public BankException(String message) {
        super(message);
    }
}
