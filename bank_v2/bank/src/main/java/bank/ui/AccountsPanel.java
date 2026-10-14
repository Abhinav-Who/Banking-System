package bank.ui;

import bank.model.Account;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** List and search accounts; open history; close an account. */
final class AccountsPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final String actor;
    private final Runnable dataChanged;
    private final Consumer<String> showHistory;
    private final JTextField searchField = new JTextField(24);
    private final AccountTableModel model = new AccountTableModel();
    private final JTable table = new JTable(model);
    private final JLabel countLabel = new JLabel();
    private String currentQuery = "";

    AccountsPanel(AppContext context, String actor, Runnable dataChanged, Consumer<String> showHistory) {
        super(new BorderLayout(0, 8));
        this.context = context;
        this.actor = actor;
        this.dataChanged = dataChanged;
        this.showHistory = showHistory;
        UiSupport.padded(this);

        JButton search = new JButton("Search");
        search.addActionListener(e -> search());
        searchField.addActionListener(e -> search());
        JButton showAll = new JButton("Show all");
        showAll.addActionListener(e -> {
            searchField.setText("");
            search();
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Account number or customer name:"));
        top.add(searchField);
        top.add(search);
        top.add(showAll);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(model));
        table.setFillsViewportHeight(true);
        UiSupport.installMoneyRenderer(table);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    viewHistory();
                }
            }
        });

        JButton history = new JButton("View history");
        history.addActionListener(e -> viewHistory());
        JButton close = new JButton("Close account");
        close.addActionListener(e -> closeSelected());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> UiSupport.attempt(this, this::refresh));
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(history);
        actions.add(close);
        bottom.add(countLabel, BorderLayout.WEST);
        bottom.add(actions, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        model.setRows(context.accounts().search(currentQuery));
        countLabel.setText(model.getRowCount() + (currentQuery.isEmpty() ? " accounts" : " matching accounts"));
    }

    private void search() {
        currentQuery = searchField.getText().trim();
        UiSupport.attempt(this, this::refresh);
    }

    private Account selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            UiSupport.info(this, "Select an account in the table first.");
            return null;
        }
        return model.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private void viewHistory() {
        Account account = selected();
        if (account != null) {
            showHistory.accept(account.getAccountNumber());
        }
    }

    private void closeSelected() {
        Account account = selected();
        if (account == null) {
            return;
        }
        String number = account.getAccountNumber();
        if (account.isClosed()) {
            UiSupport.info(this, "Account " + number + " is already closed.");
            return;
        }
        if (account.getBalance().signum() > 0) {
            String message = "Account " + number + " (" + account.getCustomerName() + ") still holds "
                    + UiSupport.money(account.getBalance()) + ".\n\n"
                    + "Pay out the full balance to the customer and close the account?\n"
                    + "This cannot be undone.";
            if (UiSupport.confirm(this, message, "Pay out and close")
                    && UiSupport.attempt(this, () -> context.accounts().payOutAndClose(actor, number))) {
                dataChanged.run();
                UiSupport.info(this, "Paid out " + UiSupport.money(account.getBalance())
                        + " and closed account " + number + ".");
            }
            return;
        }
        String message = "Close account " + number + " (" + account.getCustomerName() + ")?\nThis cannot be undone.";
        if (UiSupport.confirm(this, message, "Close account")
                && UiSupport.attempt(this, () -> context.accounts().closeAccount(actor, number))) {
            dataChanged.run();
            UiSupport.info(this, "Account " + number + " closed.");
        }
    }
}
