package bank.ui;

import bank.model.Account;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/** Balance and full transaction history of one account. */
final class HistoryPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final JTextField accountField = new JTextField(12);
    private final JLabel summary = UiSupport.hint("Enter an account number and press Load.");
    private final TransactionTableModel model = new TransactionTableModel();
    private String shownAccount;

    HistoryPanel(AppContext context) {
        super(new BorderLayout(0, 8));
        this.context = context;
        UiSupport.padded(this);

        JButton load = new JButton("Load");
        load.addActionListener(e -> load());
        accountField.addActionListener(e -> load());
        JPanel top = new JPanel(new BorderLayout());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Account number:"));
        row.add(accountField);
        row.add(load);
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        top.add(row, BorderLayout.NORTH);
        top.add(summary, BorderLayout.SOUTH);

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        UiSupport.installMoneyRenderer(table);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    void showAccount(String accountNumber) {
        accountField.setText(accountNumber);
        load();
    }

    @Override
    public void refresh() {
        if (shownAccount != null) {
            display(shownAccount);
        }
    }

    private void load() {
        UiSupport.attempt(this, () -> display(accountField.getText()));
    }

    private void display(String accountNumber) {
        Account account;
        try {
            account = context.accounts().getAccount(accountNumber);
        } catch (RuntimeException e) {
            shownAccount = null;
            model.setRows(java.util.List.of());
            summary.setText(" ");
            throw e;
        }
        model.setRows(context.transactions().history(account.getAccountNumber()));
        shownAccount = account.getAccountNumber();
        summary.setText(account.getAccountNumber() + "  ·  " + UiSupport.describe(account)
                + "  ·  " + model.getRowCount() + " transactions");
    }
}
