/*
 */
package vn.asg.converter.common;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultRowSorter;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 *
 * @author ThanhNk
 */
public abstract class ModelBase extends DefaultTableModel {

    protected JTable table;
    protected DefaultRowSorter sorter;
    protected boolean clearingData = false;

    public ModelBase() {
        super();
    }

    public ModelBase(JTable table) {
        super();
        this.table = table;
        this.table.getTableHeader().setReorderingAllowed(false);
        this.table.setModel(this);

        this.initialize();

        // sorter = ((DefaultRowSorter) table.getRowSorter());
//        if (this.table.getTableHeader() != null) {
//            this.table.getTableHeader().setReorderingAllowed(false);
//            table.getTableHeader().setFont(new Font("Arial", Font.PLAIN, 14));
//        }
    }

    public void createLineNumber() {

        JViewport jviewPort = (JViewport) this.table.getParent();
        if (jviewPort == null) {
            return;
        }

        JScrollPane jscrollPane = (JScrollPane) jviewPort.getParent();
        if (jscrollPane == null) {
            return;
        }

        TableRowNumber tableLineNumber = new TableRowNumber(jscrollPane, table);
        tableLineNumber.setBackground(Color.LIGHT_GRAY);
        jscrollPane.setRowHeaderView(tableLineNumber);

    }

    public void hideHeader() {
        this.table.setTableHeader(null);

        JViewport jviewPort = (JViewport) this.table.getParent();
        if (jviewPort == null) {
            return;
        }

        JScrollPane jscrollPane = (JScrollPane) jviewPort.getParent();
        if (jscrollPane == null) {
            return;
        }
        jscrollPane.setColumnHeader(null);
        jscrollPane.setColumnHeaderView(null);
        jscrollPane.getColumnHeader().setVisible(false);
    }

    public void setFont(Font font) {
        this.table.setFont(font);
    }

    public void setRowHeight(int height) {
        this.table.setRowHeight(height);
    }

    public synchronized void clear() {
        try {
            clearingData = true;

//	    this.sorter.allRowsChanged();
            final int count = this.getRowCount() - 1;
            for (int i = count; i >= 0; i--) {
                this.removeRow(i);
            }

        } finally {
            clearingData = false;
        }
    }

    public void setSortKey(SortKey sortkey) {
        table.setAutoCreateRowSorter(true);
        final ArrayList list = new ArrayList();
        list.add(sortkey);
        sorter = ((DefaultRowSorter) table.getRowSorter());
        if (sorter == null) {
            return;
        }
        sorter.setSortKeys(list);
        sorter.sort();
    }

    public void setSortKey(List<SortKey> sortkey) {
        table.setAutoCreateRowSorter(true);
        sorter = ((DefaultRowSorter) table.getRowSorter());
        if (sorter == null) {
            return;
        }
        sorter.setSortKeys(sortkey);
        sorter.sort();
    }

    public void setSort(int column) {
        table.setAutoCreateRowSorter(true);
        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(table.getModel());
        table.setRowSorter(sorter);

        List<RowSorter.SortKey> sortKeys = new ArrayList<>(25);
        // sortKeys.add(new RowSorter.SortKey(4, SortOrder.ASCENDING));
        sortKeys.add(new RowSorter.SortKey(column, SortOrder.ASCENDING));
        sorter.setSortKeys(sortKeys);
    }

    public void setFilter(String filter) {
        sorter = ((DefaultRowSorter) table.getRowSorter());
        if (sorter == null) {
            return;
        }
        sorter.setRowFilter(RowFilter.regexFilter(filter));
        sorter.setSortsOnUpdates(true);
    }

    public void setFilter(RowFilter rowFilter) {
        sorter = ((DefaultRowSorter) table.getRowSorter());
        if (sorter == null) {
            return;
        }
        sorter.setRowFilter(rowFilter);
        sorter.setSortsOnUpdates(true);
    }

    public int getDisplayedRow() {
        return this.sorter.getViewRowCount();
    }

    public void refresh() {
        this.sorter.sort();
    }

    public Boolean isClearingData() {
        return this.clearingData;
    }

    protected abstract void initialize();

    protected void setColumnWidth(int colIndex, int minWidth, int maxWidth, int prefer) {
        table.getColumnModel().getColumn(colIndex).setMinWidth(minWidth);
        table.getColumnModel().getColumn(colIndex).setMaxWidth(maxWidth);
        table.getColumnModel().getColumn(colIndex).setPreferredWidth(prefer);
    }

    protected void setColumnWidth(int colIndex, int minWidth, int maxWidth) {
        table.getColumnModel().getColumn(colIndex).setMinWidth(minWidth);
        table.getColumnModel().getColumn(colIndex).setMaxWidth(maxWidth);
    }

}

