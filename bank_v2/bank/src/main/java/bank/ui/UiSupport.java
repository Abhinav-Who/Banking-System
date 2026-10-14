package bank.ui;

import bank.model.Account;
import bank.repository.PersistenceException;
import bank.service.BankException;
import bank.service.BankRules;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Shared dialogs, formatting and layout helpers. No business rules here. */
final class UiSupport {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Color NEGATIVE = new Color(0xB0, 0x00, 0x20);

    private UiSupport() {
    }

    /**
     * Runs a UI action and turns any failure into a dialog. Business errors show their message;
     * storage errors explain that nothing was saved; anything unexpected is logged to the console
     * (never shown as a stack trace).
     *
     * @return true if the action completed
     */
    static boolean attempt(Component parent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (BankException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Cannot complete", JOptionPane.WARNING_MESSAGE);
        } catch (PersistenceException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage()
                            + "\n\nThe operation was cancelled and no data was changed.",
                    "Storage error", JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, "An unexpected error occurred ("
                            + e.getClass().getSimpleName() + "). No data was changed.\nDetails were written to the console.",
                    "Unexpected error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** Shows "—" for values recorded before a field existed. */
    static String orDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    static String money(BigDecimal amount) {
        return BankRules.formatMoney(amount);
    }

    static String describe(Account a) {
        return a.getCustomerName() + "  ·  " + a.getType().label() + "  ·  " + a.getStatus()
                + "  ·  Balance " + money(a.getBalance());
    }

    /** Right-aligned money cells, negatives in red. */
    static void installMoneyRenderer(JTable table) {
        table.setDefaultRenderer(BigDecimal.class, new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (value instanceof BigDecimal amount) {
                    setText(money(amount));
                    if (!selected) {
                        setForeground(amount.signum() < 0 ? NEGATIVE : t.getForeground());
                    }
                }
                return this;
            }
        });
    }

    /** Builds a two-column label/field form. */
    static JPanel form(Object... labelsAndFields) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labelsAndFields.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(String.valueOf(labelsAndFields[i])), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add((Component) labelsAndFields[i + 1], c);
        }
        return panel;
    }

    static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.GRAY);
        return label;
    }

    static <T extends JComponent> T padded(T component) {
        component.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return component;
    }
}
