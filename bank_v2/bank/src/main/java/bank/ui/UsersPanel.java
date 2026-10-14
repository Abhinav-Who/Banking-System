package bank.ui;

import bank.model.User;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;

/** Administrators only: list users, add a user, reset a password, disable or enable a user. */
final class UsersPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final String actor;
    private final UserTableModel model = new UserTableModel();
    private final JTable table = new JTable(model);
    private final JButton toggleButton = new JButton("Disable");

    UsersPanel(AppContext context, String actor) {
        super(new BorderLayout(0, 8));
        this.context = context;
        this.actor = actor;
        UiSupport.padded(this);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(new TableRowSorter<>(model));
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(e -> updateToggleLabel());

        JButton add = new JButton("Add user");
        add.addActionListener(e -> addUser());
        JButton reset = new JButton("Reset password");
        reset.addActionListener(e -> resetPassword());
        toggleButton.addActionListener(e -> toggleActive());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> UiSupport.attempt(this, this::refresh));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(add);
        actions.add(reset);
        actions.add(toggleButton);

        add(UiSupport.hint("New users and users whose password is reset must choose their own password at next login. "
                + "Disabled users cannot sign in but stay in the transaction history."), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = selectedUsername();
        model.setRows(context.auth().listUsers(actor));
        if (selected != null) {
            for (int i = 0; i < model.getRowCount(); i++) {
                if (model.rowAt(i).username().equals(selected)) {
                    int viewRow = table.convertRowIndexToView(i);
                    table.setRowSelectionInterval(viewRow, viewRow);
                }
            }
        }
        updateToggleLabel();
    }

    private String selectedUsername() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.rowAt(table.convertRowIndexToModel(viewRow)).username();
    }

    private User selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            UiSupport.info(this, "Select a user in the table first.");
            return null;
        }
        return model.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private void updateToggleLabel() {
        int viewRow = table.getSelectedRow();
        boolean active = viewRow < 0 || model.rowAt(table.convertRowIndexToModel(viewRow)).active();
        toggleButton.setText(active ? "Disable" : "Enable");
    }

    private void addUser() {
        JTextField username = new JTextField(18);
        JPasswordField password = new JPasswordField(18);
        JPasswordField confirm = new JPasswordField(18);
        JCheckBox admin = new JCheckBox("Administrator (can manage users)");
        JPanel form = UiSupport.form("Username:", username, "Temporary password:", password,
                "Confirm password:", confirm, "", admin);
        // Keep the dialog open with its contents until the input is accepted or cancelled.
        while (JOptionPane.showConfirmDialog(this, form, "Add user", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            char[] p = password.getPassword();
            char[] c = confirm.getPassword();
            try {
                User[] created = new User[1];
                if (UiSupport.attempt(this, () -> created[0] = context.auth()
                        .createUser(actor, username.getText(), p, c, admin.isSelected()))) {
                    UiSupport.attempt(this, this::refresh);
                    UiSupport.info(this, "User " + created[0].username() + " created"
                            + (created[0].admin() ? " as an administrator" : "")
                            + ".\nGive them the temporary password; they must change it at first login.");
                    return;
                }
            } finally {
                Arrays.fill(p, '\0');
                Arrays.fill(c, '\0');
            }
        }
    }

    private void resetPassword() {
        User user = selected();
        if (user == null) {
            return;
        }
        JPasswordField password = new JPasswordField(18);
        JPasswordField confirm = new JPasswordField(18);
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.add(new JLabel("New temporary password for " + user.username() + ":"), BorderLayout.NORTH);
        form.add(UiSupport.form("Temporary password:", password, "Confirm password:", confirm), BorderLayout.CENTER);
        while (JOptionPane.showConfirmDialog(this, form, "Reset password", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            char[] p = password.getPassword();
            char[] c = confirm.getPassword();
            try {
                if (UiSupport.attempt(this, () -> context.auth().resetPassword(actor, user.username(), p, c))) {
                    UiSupport.attempt(this, this::refresh);
                    UiSupport.info(this, "Password reset. " + user.username()
                            + " must choose a new password at next login.");
                    return;
                }
            } finally {
                Arrays.fill(p, '\0');
                Arrays.fill(c, '\0');
            }
        }
    }

    private void toggleActive() {
        User user = selected();
        if (user == null) {
            return;
        }
        boolean enable = !user.active();
        if (!enable && user.username().equalsIgnoreCase(actor)) {
            // Skip the pointless confirmation; the service gives the reason it is not allowed.
            UiSupport.attempt(this, () -> context.auth().setActive(actor, user.username(), false));
            return;
        }
        String question = enable
                ? "Re-enable " + user.username() + " so they can sign in again?"
                : "Disable " + user.username() + "? They will no longer be able to sign in.";
        if (UiSupport.confirm(this, question, enable ? "Enable user" : "Disable user")
                && UiSupport.attempt(this, () -> context.auth().setActive(actor, user.username(), enable))) {
            UiSupport.attempt(this, this::refresh);
        }
    }
}
