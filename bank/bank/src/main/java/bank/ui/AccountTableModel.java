package bank.ui;

import bank.model.Account;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class AccountTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Account No.", "Customer", "Phone", "Type", "Balance", "Status", "Opened"};
    private List<Account> rows = new ArrayList<>();

    void setRows(List<Account> accounts) {
        rows = new ArrayList<>(accounts);
        fireTableDataChanged();
    }

    Account rowAt(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 4 ? BigDecimal.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Account a = rows.get(row);
        return switch (column) {
            case 0 -> a.getAccountNumber();
            case 1 -> a.getCustomerName();
            case 2 -> a.getPhone();
            case 3 -> a.getType().label();
            case 4 -> a.getBalance();
            case 5 -> a.getStatus().name();
            case 6 -> UiSupport.DATE_TIME.format(a.getOpenedAt());
            default -> throw new IllegalArgumentException("column " + column);
        };
    }
}
