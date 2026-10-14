package bank.ui;

import bank.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Main window: tabs for each task, a top bar with the signed-in user and logout. */
final class MainFrame extends JFrame {

    private final AppContext context;
    private final String username;
    private final JTabbedPane tabs = new JTabbedPane();
    private final HistoryPanel historyPanel;
    private final List<Refreshable> panels = new ArrayList<>();

    MainFrame(AppContext context, User user) {
        super("Bank Management System");
        this.context = context;
        this.username = user.username();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Runnable dataChanged = this::refreshAll;
        AccountsPanel accountsPanel = new AccountsPanel(context, username, dataChanged, this::showHistory);
        historyPanel = new HistoryPanel(context);
        addTab("Accounts", accountsPanel);
        addTab("Create Account", new CreateAccountPanel(context, username, dataChanged));
        addTab("Deposit / Withdraw", new DepositWithdrawPanel(context, username, dataChanged));
        addTab("Transfer", new TransferPanel(context, username, dataChanged));
        addTab("History", historyPanel);
        // The tab is only a convenience; the service rejects non-administrators regardless.
        if (user.admin()) {
            addTab("Users", new UsersPanel(context, username));
        }
        tabs.addChangeListener(e -> {
            Component selected = tabs.getSelectedComponent();
            if (selected instanceof Refreshable r) {
                UiSupport.attempt(this, r::refresh);
            }
        });

        setLayout(new BorderLayout());
        add(topBar(user), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        setSize(1100, 650);
        setLocationRelativeTo(null);
        refreshAll();
    }

    private <T extends Component & Refreshable> void addTab(String title, T panel) {
        tabs.addTab(title, panel);
        panels.add(panel);
    }

    private JPanel topBar(User user) {
        JLabel who = new JLabel("Signed in as " + username + (user.admin() ? " (administrator)" : ""));
        JButton changePassword = new JButton("Change password");
        changePassword.addActionListener(e ->
                new ChangePasswordDialog(this, context.auth(), username, null).setVisible(true));
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> logout());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(who);
        right.add(changePassword);
        right.add(logout);
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JLabel statusBar() {
        JLabel status = new JLabel("Data folder: " + context.dataDirectory());
        status.setForeground(Color.GRAY);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return status;
    }

    /** Called after every successful operation so every table shows current data. */
    private void refreshAll() {
        UiSupport.attempt(this, () -> panels.forEach(Refreshable::refresh));
    }

    private void showHistory(String accountNumber) {
        tabs.setSelectedComponent(historyPanel);
        historyPanel.showAccount(accountNumber);
    }

    private void logout() {
        dispose();
        new LoginFrame(context).setVisible(true);
    }
}
