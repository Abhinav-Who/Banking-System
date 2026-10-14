package bank;

import bank.repository.PersistenceException;
import bank.repository.file.DataDirectoryLock;
import bank.repository.file.FileAccountRepository;
import bank.repository.file.FileStorage;
import bank.repository.file.FileTransactionRepository;
import bank.repository.file.FileUnitOfWork;
import bank.repository.file.FileUserRepository;
import bank.service.AccountService;
import bank.service.AuthService;
import bank.service.PasswordHasher;
import bank.service.TransactionService;
import bank.ui.AppContext;
import bank.ui.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/** Wires repositories, services and UI together. */
public final class Main {

    /** Overrides the data folder: {@code java -Dbank.data.dir=/path -jar bank.jar}. */
    public static final String DATA_DIR_PROPERTY = "bank.data.dir";

    // Held for the life of the process so the OS lock is not released.
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static DataDirectoryLock dataLock;

    private Main() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("This application needs a graphical display.");
            System.exit(1);
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "An unexpected error occurred (" + e.getClass().getSimpleName()
                            + "). Details were written to the console.",
                    "Unexpected error", JOptionPane.ERROR_MESSAGE));
        });

        Path dataDir = resolveDataDirectory();
        AppContext context;
        try {
            dataLock = DataDirectoryLock.acquire(dataDir);
            FileStorage storage = new FileStorage(dataDir);
            FileUnitOfWork unitOfWork = new FileUnitOfWork(storage);
            FileAccountRepository accounts = new FileAccountRepository(storage, unitOfWork);
            FileTransactionRepository transactions = new FileTransactionRepository(storage, unitOfWork);
            FileUserRepository users = new FileUserRepository(storage, unitOfWork);

            Clock clock = Clock.systemDefaultZone();
            AuthService auth = new AuthService(users, new PasswordHasher());
            auth.ensureDefaultAdmin();
            context = new AppContext(auth,
                    new AccountService(accounts, transactions, unitOfWork, clock),
                    new TransactionService(accounts, transactions, unitOfWork, clock),
                    storage.directory());
        } catch (PersistenceException e) {
            System.err.println(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        System.out.println("Data folder: " + context.dataDirectory());

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default look and feel is fine.
            }
            new LoginFrame(context).setVisible(true);
        });
    }

    /**
     * {@code -Dbank.data.dir} if given; otherwise a {@code data} folder next to the jar;
     * when not running from a jar (e.g. from an IDE), {@code data} in the working directory.
     */
    static Path resolveDataDirectory() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        try {
            Path codeSource = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codeSource)) {
                return codeSource.toAbsolutePath().getParent().resolve("data").normalize();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException | IllegalArgumentException ignored) {
            // fall through
        }
        return Path.of("data").toAbsolutePath().normalize();
    }
}
