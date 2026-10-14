package bank.ui;

import bank.service.AuthService;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Arrays;

/**
 * Change-password dialog. In forced mode (first login) the current password is already known
 * from the login screen, so only the new password is asked for, and cancelling returns to login.
 */
final class ChangePasswordDialog extends JDialog {

    private final AuthService auth;
    private final String username;
    private final char[] knownCurrentPassword;
    private final JPasswordField currentField = new JPasswordField(18);
    private final JPasswordField newField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);
    private boolean changed;

    /**
     * @param knownCurrentPassword the password just used to log in (forced mode), or null to ask for it
     */
    ChangePasswordDialog(Window owner, AuthService auth, String username, char[] knownCurrentPassword) {
        super(owner, "Change password", ModalityType.APPLICATION_MODAL);
        this.auth = auth;
        this.username = username;
        this.knownCurrentPassword = knownCurrentPassword == null ? null : knownCurrentPassword.clone();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel form = knownCurrentPassword == null
                ? UiSupport.form("Current password:", currentField, "New password:", newField,
                "Confirm new password:", confirmField)
                : UiSupport.form("New password:", newField, "Confirm new password:", confirmField);

        JLabel intro = new JLabel(knownCurrentPassword == null
                ? "Choose a new password (at least " + AuthService.MIN_PASSWORD_LENGTH + " characters)."
                : "<html>You are using the default password.<br>Please choose a new one (at least "
                + AuthService.MIN_PASSWORD_LENGTH + " characters) to continue.</html>");

        JButton ok = new JButton("Change password");
        ok.addActionListener(e -> submit());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = UiSupport.padded(new JPanel(new BorderLayout(0, 8)));
        content.add(intro, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    boolean isChanged() {
        return changed;
    }

    private void submit() {
        char[] current = knownCurrentPassword != null ? knownCurrentPassword.clone() : currentField.getPassword();
        char[] next = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        try {
            if (UiSupport.attempt(this, () -> auth.changePassword(username, current, next, confirm))) {
                changed = true;
                UiSupport.info(this, "Password changed.");
                dispose();
            }
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    @Override
    public void dispose() {
        if (knownCurrentPassword != null) {
            Arrays.fill(knownCurrentPassword, '\0');
        }
        super.dispose();
    }
}
