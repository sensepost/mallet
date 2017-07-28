package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;

import com.sensepost.mallet.graph.GraphNode;

public class GraphNodeEditor extends JPanel implements TableModelListener {
	private JTable table;
	private DefaultTableModel tableModel = new DefaultTableModel();
	private JTextField classField;
	private JButton cancelButton, okButton;

	public GraphNodeEditor() {
		setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		add(panel, BorderLayout.NORTH);
		panel.setLayout(new BorderLayout(0, 0));

		JLabel lblClass = new JLabel("Class");
		panel.add(lblClass, BorderLayout.WEST);

		classField = new JTextField();
		panel.add(classField, BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		add(panel_1, BorderLayout.EAST);
		panel_1.setLayout(new GridLayout(0, 1, 0, 0));

		JButton addParamButton = new JButton("+");
		addParamButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				tableModel.addRow(new String[tableModel.getColumnCount()]);
			}
		});

		JButton moveUpParameterButton = new JButton("^");
		moveUpParameterButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row < 1)
					return;
				int c = tableModel.getColumnCount();
				Object[] data = new Object[c];
				for (int i = 0; i < c; i++)
					data[i] = tableModel.getValueAt(row, i);
				tableModel.removeRow(row);
				tableModel.insertRow(row - 1, data);
			}
		});
		panel_1.add(moveUpParameterButton);
		panel_1.add(addParamButton);

		JButton removeParamButton = new JButton("-");
		removeParamButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row == -1)
					return;
				tableModel.removeRow(row);
			}
		});
		panel_1.add(removeParamButton);

		JButton moveParameterDownButton = new JButton("v");
		moveParameterDownButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row == -1 || row == table.getRowCount() - 1)
					return;
				int c = tableModel.getColumnCount();
				Object[] data = new Object[c];
				for (int i = 0; i < c; i++)
					data[i] = tableModel.getValueAt(row, i);
				tableModel.removeRow(row);
				tableModel.insertRow(row + 1, data);
			}
		});
		panel_1.add(moveParameterDownButton);

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		tableModel.addColumn("Parameter");

		table = new JTable(tableModel);
		table.setGridColor(Color.GRAY);
		table.setShowGrid(true);
		scrollPane.setViewportView(table);

		RowHeightCellRenderer dynRow = new RowHeightCellRenderer();
		table.getColumnModel().getColumn(0).setCellRenderer(dynRow);
		table.getColumnModel().getColumn(0).setCellEditor(new MultilineTableCellEditor());

		// No more data changes; install listeners
		table.getModel().addTableModelListener(this);
		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			/**
			 * We only need to recalculate once; so track if we are already
			 * going to do it.
			 */
			boolean columnHeightWillBeCalculated = false;

			@Override
			public void columnAdded(TableColumnModelEvent e) {
			}

			@Override
			public void columnRemoved(TableColumnModelEvent e) {
			}

			@Override
			public void columnMoved(TableColumnModelEvent e) {
			}

			@Override
			public void columnMarginChanged(ChangeEvent e) {
				if (!columnHeightWillBeCalculated && table.getTableHeader().getResizingColumn() != null) {
					columnHeightWillBeCalculated = true;
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							// textTable.getTableHeader().getResizingColumn() is
							// != null as long as the user still is holding the
							// mouse down
							// To avoid going over all data every few
							// milliseconds wait for user to release
							if (table.getTableHeader().getResizingColumn() != null) {
								SwingUtilities.invokeLater(this);
							} else {
								tableChanged(null);
								columnHeightWillBeCalculated = false;
							}
						}
					});
				}
			}

			@Override
			public void columnSelectionChanged(ListSelectionEvent e) {
			}
		});

		JPanel panel_2 = new JPanel();
		add(panel_2, BorderLayout.SOUTH);

		cancelButton = new JButton();
		panel_2.add(cancelButton);

		okButton = new JButton();
		panel_2.add(okButton);
	}

	public void tableChanged(TableModelEvent e) {
		final int first;
		final int last;
		if (e == null || e.getFirstRow() == TableModelEvent.HEADER_ROW) {
			// assume everything changed
			first = 0;
			last = table.getModel().getRowCount();
		} else {
			first = e.getFirstRow();
			last = e.getLastRow() + 1;
		}
		// GUI-Changes should be done through the EventDispatchThread which
		// ensures all pending events were processed
		// Also this way nobody will change the text of our
		// RowHeightCellRenderer because a cell is to be rendered
		if (SwingUtilities.isEventDispatchThread()) {
			updateRowHeights(first, last);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					updateRowHeights(first, last);
				}
			});
		}
	}

	private void updateRowHeights(final int first, final int last) {
		/*
		 * Auto adjust the height of rows in a JTable. The only way to know the
		 * row height for sure is to render each cell to determine the rendered
		 * height. After your table is populated with data you can do:
		 *
		 */
		for (int row = first; row < last && row < table.getRowCount(); row++) {
			int rowHeight = 20;
			for (int column = 0; column < table.getColumnCount(); column++) {
				Component comp = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
				rowHeight = Math.max(rowHeight, comp.getPreferredSize().height);
			}
			if (rowHeight != table.getRowHeight(row)) {
				table.setRowHeight(row, rowHeight);
			}
		}
	}

	public void setActions(Action cancelAction, Action okAction) {
		cancelButton.setAction(cancelAction);
		okButton.setAction(okAction);
	}

	public GraphNode getGraphNode() {
		String className = classField.getText();
		int r = tableModel.getRowCount();
		String[] args = new String[r];
		for (int i = 0; i < r; i++) {
			args[i] = (String) tableModel.getValueAt(i, 0);
		}
		GraphNode node = new GraphNode();
		node.setClassName(className);
		node.setArguments(args);
		return node;
	}

	public void setGraphNode(GraphNode node) {
		int r = tableModel.getRowCount();
		for (int i = 0; i < r; i++)
			tableModel.removeRow(0);

		if (node == null) {
			classField.setText("");
		} else {
			classField.setText(node.getClassName());
			String[] args = node.getArguments();
			if (args == null)
				args = new String[0];
			for (int i = 0; i < args.length; i++)
				tableModel.addRow(new String[] { args[i] });
		}
	}

	private static class RowHeightCellRenderer extends JTextArea implements TableCellRenderer {

		private static final long serialVersionUID = 1L;

		public RowHeightCellRenderer() {
			setLineWrap(true);
			setWrapStyleWord(true);
		}// constructor

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			this.setText((String) value);

			if (isSelected) {
				this.setBackground(table.getSelectionBackground());
				this.setForeground(table.getSelectionForeground());
			} else {
				this.setBackground(table.getBackground());
				this.setForeground(table.getForeground());
			}

			final int columnWidth = table.getColumnModel().getColumn(column).getWidth();
			final int rowHeight = table.getRowHeight(row);
			this.setSize(columnWidth, rowHeight);

			this.validate();
			return this;
		}// getTableCellRendererComponent

		@Override
		public Dimension getPreferredSize() {
			try {
				// Get Rectangle for position after last text-character
				final Rectangle rectangle = this.modelToView(getDocument().getLength());
				if (rectangle != null) {
					return new Dimension(this.getWidth(),
							this.getInsets().top + rectangle.y + rectangle.height + this.getInsets().bottom);
				}
			} catch (BadLocationException e) {
				e.printStackTrace(); // TODO: implement catch
			}

			return super.getPreferredSize();
		}
	}

	private static class MultilineTableCellEditor extends AbstractCellEditor implements TableCellEditor {

		JComponent component = new JTextArea();

		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int rowIndex,
				int vColIndex) {

			((JTextArea) component).setText((String) value);

			return component;
		}

		public Object getCellEditorValue() {
			return ((JTextArea) component).getText();
		}
	}
}
