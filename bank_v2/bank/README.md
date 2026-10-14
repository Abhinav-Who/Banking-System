# Bank Management System

A desktop bank management application in Java 17 with a Swing UI and plain-file persistence.
No runtime dependencies; JUnit 5 is used for tests only.

## Build and run

Requirements: JDK 17 or newer, Maven 3.6+.

```bash
mvn package                 # compiles, runs the tests, builds target/bank.jar
java -jar target/bank.jar
```

### Where the data lives

By default the data is stored in a `data/` folder **next to the jar**, i.e. `target/data/` when you
run it as shown above.

> **Warning:** `mvn clean` deletes `target/`, and with it all accounts and transactions.
> For anything beyond trying the app out, copy the jar somewhere else, or point the app at a
> permanent folder:
>
> ```bash
> java -Dbank.data.dir=/path/to/bank-data -jar target/bank.jar
> ```

The data folder in use is shown at the bottom of the main window and printed on startup.
When run from an IDE (classes rather than a jar), the default is `data/` in the working directory.

## Default login

| Username | Password   |
|----------|------------|
| `admin`  | `admin123` |

The admin account is created on first run. At the first login you must choose a new password
(at least 8 characters, different from the current one and from `admin123`) before the main
window opens. Cancelling returns you to the login screen.

After that, the admin creates a login for each member of staff in the **Users** tab.

## Users and permissions

There are two levels of user:

| Role | Can do |
|------|--------|
| **Staff** | All banking work: open, search and close accounts; deposit, withdraw, transfer; view history. |
| **Administrator** | Everything staff can do, plus the **Users** tab: add users, reset passwords, disable and re-enable users. |

- New users get a **temporary password** from the administrator and must choose their own at first login.
  The same happens after an administrator resets someone's password.
- **Disabled** users cannot sign in, but they are never deleted, so their name stays in the history.
- You cannot disable yourself. Because only an active administrator can disable anyone, this
  guarantees at least one active administrator always remains.
- Administrators change their own password with **Change password** (which asks for the current
  one), not with Reset password.
- Every account records who opened it (**Opened by**), and every transaction records who performed it
  (**By** column in History).
- Usernames: 3–32 characters, starting with a letter; letters, digits, `.`, `_`, `-`. Not case-sensitive
  for sign-in and uniqueness.

**Forgotten password:** an administrator resets it in the Users tab. If the *only* administrator forgets
theirs, the sole recovery is to close the app and delete `users.csv`: the next start recreates
`admin` / `admin123`, but **every staff login is lost** and must be created again (accounts, balances
and history are untouched, and old names stay in the history). Keep at least two administrators so
this is never needed.

## Features

- **Accounts tab** – lists every account; search by account number (any part) or customer name
  (case-insensitive); view history (button or double-click); close an account.
- **Create Account** – customer name, phone, type (Savings/Current), opening deposit. A unique
  10-digit account number is generated and shown.
- **Deposit / Withdraw** – look up an account to see its balance, then deposit or withdraw.
- **Transfer** – between two accounts, with a confirmation dialog naming both customers.
- **History** – balance and full transaction history (date, type, amount, balance after,
  reference account for transfers, and who performed it).
- **Users** (administrators only) – see [Users and permissions](#users-and-permissions).
- **Change password** and **Logout** in the top bar.

All tables refresh after every operation. Errors are shown as dialogs; no stack trace is ever shown
to the user (unexpected errors are logged to the console instead).

## Business rules

All enforced in the service layer (`bank.service`); the UI checks input early only for convenience.

| Rule | Detail |
|------|--------|
| Amounts | Greater than 0, at most 2 decimal places, typed as digits (`1500` or `1500.50`). Sanity cap of 999,999,999,999.99 per amount. |
| Savings | Balance may never go below 1,000.00. The opening deposit must therefore be at least 1,000.00. |
| Current | Overdraft to −5,000.00 allowed; nothing below. |
| Transfer | Atomic. Not to the same account, nor to/from a closed or non-existent account. |
| Closed accounts | Remain listed with status `CLOSED` and history visible; every transaction is rejected. |
| Audit trail | Every balance change creates a transaction record (enforced by a single `Ledger` code path). |

## Architecture

```
bank.model        Account (abstract), SavingsAccount, CurrentAccount, Transaction, User, enums
bank.repository   AccountRepository, TransactionRepository, UserRepository, UnitOfWork (interfaces)
  └─ file         File-based implementations, FileStorage, FileUnitOfWork, DataDirectoryLock
bank.service      AccountService, TransactionService, AuthService, BankRules, PasswordHasher, Ledger, Actors
bank.ui           Swing frames and panels (call services only)
bank.Main         Wires everything together
```

## Design decisions

These fill gaps in the original specification. Each is the simplest option that satisfies it.

1. **Closing a Savings account.** The spec requires a zero balance to close, but Savings
   withdrawals must leave 1,000.00, so a Savings account could never reach zero. Resolution:
   "Close account" on an account with a positive balance offers **Pay out and close**, which records
   a `CLOSING_PAYOUT` transaction for the whole balance and closes the account in one atomic step.
   The account is at exactly 0.00 when it closes. Ordinary withdrawals and transfers still obey the
   minimum balance. An overdrawn Current account cannot be closed until it is brought to zero.

2. **Atomic multi-file saves.** Accounts and transactions live in separate files, and two renames
   cannot be made atomic together, so "write a temp file then rename" alone cannot make a transfer
   atomic. `FileStorage` therefore uses a small commit journal:
   write every `*.tmp` file and fsync → write `commit.journal` (the commit point, via atomic rename)
   → rename each temp file over the real one → delete the journal. On startup an existing journal is
   completed (roll forward) and any other `*.tmp` file is discarded (roll back). A crash at any
   moment leaves either the complete old state or the complete new state.

3. **In-memory state with rollback.** Repositories hold data in memory and write whole files.
   Each service operation runs in a `UnitOfWork`; if the operation fails, or the disk write fails,
   every repository is restored from a snapshot, so memory and disk never disagree.

4. **Single instance per data folder.** A lock file (`data/bank.lock`) stops a second copy of the
   app from using the same folder, because it would silently overwrite the first one's changes.

5. **Passwords.** PBKDF2WithHmacSHA256, 16-byte random salt per user, 256-bit key,
   210,000 iterations (the iteration count is stored per user, so it can be raised later without
   breaking existing logins). OWASP currently recommends 600,000 for this algorithm; 210,000 was
   chosen to keep login responsive on slow machines. Wrong password and unknown user give the same
   message and take the same time. Usernames are case-insensitive.

6. **Account numbers.** Random 10-digit numbers from `SecureRandom`, never starting with 0, checked
   for uniqueness. Random rather than sequential so numbers are not guessable.

7. **Customer details.** Name: required, max 100 characters, whitespace collapsed. Phone: 7–15
   digits with an optional leading `+`; spaces and hyphens are removed before storing.

8. **Money.** `BigDecimal` at scale 2 everywhere. Setters use `RoundingMode.UNNECESSARY`, so any code
   path that would silently round money fails loudly instead. Amounts are displayed as `1,234.50`
   in a fixed format regardless of system locale; negatives are shown in red.

9. **File format.** UTF-8 CSV with a `#` header line, quoting fields that contain commas, quotes or a
   leading `#`. A damaged line stops startup with the file name and line number rather than being
   skipped, because silently dropping a balance or transaction is worse than refusing to start.

10. **Transaction timestamps** use the system clock to the second. Both sides of a transfer share
    one timestamp.

11. **Two roles, despite "multiple roles" being out of scope in the original brief.** Multiple staff
    logins were added later. Without an administrator level, any teller could create users or reset
    the administrator's password, so a minimal Staff/Administrator split was accepted as necessary.

12. **The acting user is passed to every service call and checked there.** Each banking method takes
    the username of whoever is performing it; the service verifies it is an existing, active user
    and records the stored spelling. User-management methods additionally require an administrator.
    The UI hiding the Users tab is only a convenience, never the protection.

13. **Disable, never delete.** Deleting a user would leave transaction records pointing at a name
    that no longer exists. Disabling keeps the audit trail intact.

14. **Old data folders keep working.** Files written by the single-user version load unchanged:
    the old `admin` becomes an active administrator, and older accounts and transactions show "—" for
    who did them. The files are rewritten in the new format on the next change.

## Data files

| File | Contents |
|------|----------|
| `accounts.csv` | accountNumber, type, customerName, phone, balance, status, openedAt, openedBy |
| `transactions.csv` | id, accountNumber, timestamp, type, amount, balanceAfter, referenceAccount, performedBy |
| `users.csv` | username, salt, password hash, iterations, mustChangePassword, admin, active, createdAt |
| `bank.lock` | Single-instance lock (empty) |

## Tests

`mvn test` runs 58 JUnit 5 tests covering: amount/number/phone validation; minimum balance and
overdraft boundaries (exactly at the limit and 0.01 past it); transfer rules; transfer atomicity
when the disk write fails (memory and files both unchanged); crash recovery in both directions of
the journal protocol; persistence across restart; CSV escaping; closing rules; that every balance
equals the sum of its transactions; password hashing, salting and the forced-change flow;
user management permissions enforced in the service; disabled users; recording who did what;
and loading data folders written by the single-user version.

## Known limitations

- **Whole-file rewrites.** Every operation rewrites the changed files completely. Fine for
  thousands of accounts and tens of thousands of transactions; it will slow down beyond that.
  A database (out of scope) would be the fix.
- **All data in memory** while the app runs.
- **One person signed in at a time.** The app is a single desktop instance per data folder; staff
  share the machine and sign in and out.
- **User management is not itself audited.** Who created, reset or disabled a user is not recorded.
- **Roles are fixed at creation.** There is no way to promote staff to administrator or demote an
  administrator afterwards; create a new user instead.
- **No login lockout** after repeated failed attempts.
- **Plaintext data files.** Balances and customer details are readable by anyone with access to the
  data folder. Only passwords are protected (hashed). Restrict folder permissions accordingly.
- **No backups.** Copy the data folder while the app is closed to back it up.
- **Durability depends on the OS.** fsync of files is always done; fsync of the directory is skipped
  on Windows, where the OS does not support it.
- The window layout is basic Swing and has not been tuned for high-DPI displays.
