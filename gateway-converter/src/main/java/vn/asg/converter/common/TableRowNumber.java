package vn.asg.converter.common;

import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

public class TableRowNumber extends JComponent {

    private final JTable table;
    private final JScrollPane scrollPane;

    public TableRowNumber(JScrollPane jScrollPane, JTable table) {
        this.scrollPane = jScrollPane;
        this.table = table;
        this.table.getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent tme) {
                TableRowNumber.this.repaint();
            }
        });

        this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent lse) {
                TableRowNumber.this.repaint();
            }
        });

        this.scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent ae) {
                TableRowNumber.this.repaint();
            }
        });

        setPreferredSize(new Dimension(40, 60));

    }

    @Override
    protected void paintComponent(Graphics g) {
        Point viewPosition = scrollPane.getViewport().getViewPosition();
        Dimension viewSize = scrollPane.getViewport().getViewSize();
        if (getHeight() < viewSize.height) {
            Dimension size = getPreferredSize();
            size.height = viewSize.height;
            setSize(size);
            setPreferredSize(size);
        }

        super.paintComponent(g);

        g.setColor(scrollPane.getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        FontMetrics fm = g.getFontMetrics();

        for (int r = 0; r < table.getRowCount(); r++) {
            Rectangle cellRect = table.getCellRect(r, 0, false);
            boolean rowSelected = false;

            g.setColor(rowSelected ? table.getSelectionBackground() : getBackground());
            g.fillRect(0, cellRect.y, getWidth(), cellRect.height);

            if ((cellRect.y + cellRect.height) - viewPosition.y >= 0 && cellRect.y < viewPosition.y + viewSize.height) {
                g.setColor(table.getGridColor());
                g.setColor(rowSelected ? table.getSelectionForeground() : getForeground());
                String s = Integer.toString(r + 1);
                g.drawString(s, getWidth() - fm.stringWidth(s) - 8, cellRect.y + cellRect.height - fm.getDescent() - 2);
            }
        }

    }
}

