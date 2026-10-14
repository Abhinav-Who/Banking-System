package bank.ui;

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
import java.util.List;

/** Main window: tabs for each task, a top bar with the signed-in user and logout. */
final class MainFrame extends JFrame {

    private final AppContext context;
    private final String username;
    private final JTabbedPane tabs = new JTabbedPane();
    private final AccountsPanel accountsPanel;
    private final HistoryPanel historyPanel;
    private final List<Refreshable> panels;

    MainFrame(AppContext context, String username) {
        super("Bank Management System");
        this.context = context;
        this.username = username;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Runnable dataChanged = this::refreshAll;
        accountsPanel = new AccountsPanel(context, dataChanged, this::showHistory);
        CreateAccountPanel createPanel = new CreateAccountPanel(context, dataChanged);
        DepositWithdrawPanel cashPanel = new DepositWithdrawPanel(context, dataChanged);
        TransferPanel transferPanel = new TransferPanel(context, dataChanged);
        historyPanel = new HistoryPanel(context);
        panels = List.of(accountsPanel, createPanel, cashPanel, transferPanel, historyPanel);

        tabs.addTab("Accounts", accountsPanel);
        tabs.addTab("Create Account", createPanel);
        tabs.addTab("Deposit / Withdraw", cashPanel);
        tabs.addTab("Transfer", transferPanel);
        tabs.addTab("History", historyPanel);
        tabs.addChangeListener(e -> {
            Component selected = tabs.getSelectedComponent();
            if (selected instanceof Refreshable r) {
                UiSupport.attempt(this, r::refresh);
            }
        });

        setLayout(new BorderLayout());
        add(topBar(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        setSize(1050, 650);
        setLocationRelativeTo(null);
        refreshAll();
    }

    private JPanel topBar() {
        JLabel who = new JLabel("Signed in as " + username);
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
