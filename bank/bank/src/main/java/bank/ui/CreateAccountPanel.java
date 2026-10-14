package bank.ui;

import bank.model.Account;
import bank.model.AccountType;
import bank.service.BankRules;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;

final class CreateAccountPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final Runnable dataChanged;
    private final JTextField nameField = new JTextField(28);
    private final JTextField phoneField = new JTextField(16);
    private final JComboBox<AccountType> typeBox = new JComboBox<>(AccountType.values());
    private final JTextField depositField = new JTextField(12);

    CreateAccountPanel(AppContext context, Runnable dataChanged) {
        super(new BorderLayout());
        this.context = context;
        this.dataChanged = dataChanged;
        UiSupport.padded(this);

        JButton create = new JButton("Create account");
        create.addActionListener(e -> create());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> clear());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(create);
        buttons.add(clear);

        JPanel form = UiSupport.form(
                "Customer name:", nameField,
                "Phone:", phoneField,
                "Account type:", typeBox,
                "Opening deposit:", depositField,
                "", UiSupport.hint("Savings: minimum balance " + UiSupport.money(BankRules.SAVINGS_MINIMUM_BALANCE)
                        + " (also the minimum opening deposit).  Current: overdraft up to "
                        + UiSupport.money(BankRules.CURRENT_OVERDRAFT_LIMIT) + "."),
                "", buttons);
        JPanel north = new JPanel(new BorderLayout());
        north.add(form, BorderLayout.WEST);
        add(north, BorderLayout.NORTH);
    }

    @Override
    public void refresh() {
        // Form only; nothing to reload.
    }

    private void create() {
        AccountType type = (AccountType) typeBox.getSelectedItem();
        UiSupport.attempt(this, () -> {
            // Check input before submitting; the service validates again.
            String name = BankRules.validateCustomerName(nameField.getText());
            String phone = BankRules.validatePhone(phoneField.getText());
            BigDecimal deposit = BankRules.parseAmount(depositField.getText());
            Account account = context.accounts().openAccount(name, phone, type, deposit);
            dataChanged.run();
            clear();
            UiSupport.info(this, "Account created.\n\nAccount number: " + account.getAccountNumber()
                    + "\nCustomer: " + account.getCustomerName()
                    + "\nType: " + account.getType().label()
                    + "\nBalance: " + UiSupport.money(account.getBalance()));
        });
    }

    private void clear() {
        nameField.setText("");
        phoneField.setText("");
        depositField.setText("");
        typeBox.setSelectedIndex(0);
        nameField.requestFocusInWindow();
    }
}
