package bank.ui;

import bank.service.AccountService;
import bank.service.AuthService;
import bank.service.TransactionService;

import java.nio.file.Path;

/** The services the UI is allowed to talk to. */
public record AppContext(AuthService auth,
                         AccountService accounts,
                         TransactionService transactions,
                         Path dataDirectory) {
}
