package bank.ui;

import bank.model.Account;
import bank.model.Transaction;
import bank.service.BankException;
import bank.service.BankRules;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;

final class TransferPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final String actor;
    private final Runnable dataChanged;
    private final JTextField fromField = new JTextField(12);
    private final JTextField toField = new JTextField(12);
    private final JTextField amountField = new JTextField(12);

    TransferPanel(AppContext context, String actor, Runnable dataChanged) {
        super(new BorderLayout());
        this.context = context;
        this.actor = actor;
        this.dataChanged = dataChanged;
        UiSupport.padded(this);

        JButton transfer = new JButton("Transfer");
        transfer.addActionListener(e -> transfer());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(transfer);

        JPanel form = UiSupport.form(
                "From account:", fromField,
                "To account:", toField,
                "Amount:", amountField,
                "", UiSupport.hint("You will be asked to confirm before any money moves."),
                "", buttons);
        JPanel north = new JPanel(new BorderLayout());
        north.add(form, BorderLayout.WEST);
        add(north, BorderLayout.NORTH);
    }

    @Override
    public void refresh() {
        // Form only; nothing to reload.
    }

    private void transfer() {
        String[] numbers = new String[2];
        BigDecimal[] amount = new BigDecimal[1];
        Account[] parties = new Account[2];
        boolean valid = UiSupport.attempt(this, () -> {
            numbers[0] = BankRules.validateAccountNumber(fromField.getText());
            numbers[1] = BankRules.validateAccountNumber(toField.getText());
            if (numbers[0].equals(numbers[1])) {
                throw new BankException("Cannot transfer to the same account.");
            }
            amount[0] = BankRules.parseAmount(amountField.getText());
            parties[0] = context.accounts().getAccount(numbers[0]);
            parties[1] = context.accounts().getAccount(numbers[1]);
        });
        if (!valid) {
            return;
        }
        String message = "Transfer " + UiSupport.money(amount[0]) + "\n\nfrom  " + numbers[0] + "  ("
                + parties[0].getCustomerName() + ")\nto      " + numbers[1] + "  ("
                + parties[1].getCustomerName() + ")?";
        if (!UiSupport.confirm(this, message, "Confirm transfer")) {
            return;
        }
        UiSupport.attempt(this, () -> {
            Transaction debit = context.transactions().transfer(actor, numbers[0], numbers[1], amount[0]);
            amountField.setText("");
            dataChanged.run();
            UiSupport.info(this, "Transferred " + UiSupport.money(debit.amount()) + " from " + numbers[0]
                    + " to " + numbers[1] + ".\nNew balance of " + numbers[0] + ": "
                    + UiSupport.money(debit.balanceAfter()));
        });
    }
}
