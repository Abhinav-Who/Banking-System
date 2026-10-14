package bank.ui;

import bank.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;

/** First screen. On success opens the main window, forcing a password change first if required. */
public final class LoginFrame extends JFrame {

    private final AppContext context;
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);

    public LoginFrame(AppContext context) {
        super("Bank Management System — Sign in");
        this.context = context;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel title = new JLabel("Bank Management System", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));

        JButton loginButton = new JButton("Sign in");
        loginButton.addActionListener(e -> login());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(loginButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(title, BorderLayout.NORTH);
        content.add(UiSupport.padded(UiSupport.form("Username:", usernameField, "Password:", passwordField)),
                BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(loginButton);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void login() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        try {
            User[] result = new User[1];
            if (!UiSupport.attempt(this, () -> result[0] = context.auth().login(username, password))) {
                passwordField.setText("");
                passwordField.requestFocusInWindow();
                return;
            }
            User user = result[0];
            if (user.mustChangePassword()) {
                ChangePasswordDialog dialog = new ChangePasswordDialog(this, context.auth(), user.username(), password);
                dialog.setVisible(true);
                if (!dialog.isChanged()) {
                    passwordField.setText("");
                    return;
                }
            }
            new MainFrame(context, user.username()).setVisible(true);
            dispose();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
