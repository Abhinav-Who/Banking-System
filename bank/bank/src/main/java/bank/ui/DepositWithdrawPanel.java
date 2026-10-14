package bank.ui;

import bank.model.Account;
import bank.model.Transaction;
import bank.service.BankRules;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;

final class DepositWithdrawPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final Runnable dataChanged;
    private final JTextField accountField = new JTextField(12);
    private final JTextField amountField = new JTextField(12);
    private final JLabel accountInfo = UiSupport.hint("Enter an account number and press Look up.");

    DepositWithdrawPanel(AppContext context, Runnable dataChanged) {
        super(new BorderLayout());
        this.context = context;
        this.dataChanged = dataChanged;
        UiSupport.padded(this);

        JButton lookup = new JButton("Look up");
        lookup.addActionListener(e -> lookUp());
        accountField.addActionListener(e -> lookUp());
        JPanel accountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accountRow.add(accountField);
        accountRow.add(lookup);

        JButton deposit = new JButton("Deposit");
        deposit.addActionListener(e -> submit(true));
        JButton withdraw = new JButton("Withdraw");
        withdraw.addActionListener(e -> submit(false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(deposit);
        buttons.add(withdraw);

        JPanel form = UiSupport.form(
                "Account number:", accountRow,
                "", accountInfo,
                "Amount:", amountField,
                "", buttons);
        JPanel north = new JPanel(new BorderLayout());
        north.add(form, BorderLayout.WEST);
        add(north, BorderLayout.NORTH);
    }

    @Override
    public void refresh() {
        // Keep the shown balance current if another tab changed it.
        String text = accountField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            accountInfo.setText(UiSupport.describe(context.accounts().getAccount(text)));
        } catch (RuntimeException e) {
            accountInfo.setText(" ");
        }
    }

    private void lookUp() {
        UiSupport.attempt(this, () -> {
            Account account = context.accounts().getAccount(accountField.getText());
            accountInfo.setText(UiSupport.describe(account));
        });
    }

    private void submit(boolean isDeposit) {
        UiSupport.attempt(this, () -> {
            String number = BankRules.validateAccountNumber(accountField.getText());
            BigDecimal amount = BankRules.parseAmount(amountField.getText());
            Transaction tx = isDeposit
                    ? context.transactions().deposit(number, amount)
                    : context.transactions().withdraw(number, amount);
            amountField.setText("");
            dataChanged.run();
            UiSupport.info(this, (isDeposit ? "Deposited " : "Withdrew ") + UiSupport.money(tx.amount())
                    + (isDeposit ? " to " : " from ") + number
                    + ".\nNew balance: " + UiSupport.money(tx.balanceAfter()));
        });
    }
}
