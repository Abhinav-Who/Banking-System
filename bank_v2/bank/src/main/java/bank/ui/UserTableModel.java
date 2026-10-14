package bank.ui;

import bank.model.User;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class UserTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Username", "Role", "Status", "Password", "Created"};
    private List<User> rows = new ArrayList<>();

    void setRows(List<User> users) {
        rows = new ArrayList<>(users);
        fireTableDataChanged();
    }

    User rowAt(int modelRow) {
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
    public Object getValueAt(int row, int column) {
        User u = rows.get(row);
        return switch (column) {
            case 0 -> u.username();
            case 1 -> u.admin() ? "Administrator" : "Staff";
            case 2 -> u.active() ? "Active" : "Disabled";
            case 3 -> u.mustChangePassword() ? "Must change at next login" : "Set";
            case 4 -> u.createdAt() == null ? UiSupport.orDash(null) : UiSupport.DATE_TIME.format(u.createdAt());
            default -> throw new IllegalArgumentException("column " + column);
        };
    }
}
