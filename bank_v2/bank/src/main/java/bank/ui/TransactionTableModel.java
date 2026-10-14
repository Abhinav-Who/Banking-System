package bank.ui;

import bank.model.Transaction;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class TransactionTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Date", "Type", "Amount", "Balance After", "Reference Account", "By"};
    private List<Transaction> rows = new ArrayList<>();

    void setRows(List<Transaction> transactions) {
        rows = new ArrayList<>(transactions);
        fireTableDataChanged();
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
        return column == 2 || column == 3 ? BigDecimal.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Transaction t = rows.get(row);
        return switch (column) {
            case 0 -> UiSupport.DATE_TIME.format(t.timestamp());
            case 1 -> t.type().label();
            case 2 -> t.signedAmount();
            case 3 -> t.balanceAfter();
            case 4 -> t.referenceAccount() == null ? "" : t.referenceAccount();
            case 5 -> UiSupport.orDash(t.performedBy());
            default -> throw new IllegalArgumentException("column " + column);
        };
    }
}
