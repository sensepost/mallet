package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.sensepost.mallet.graph.GraphNode;

public class GraphNodeEditor extends JPanel {
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

		table = new JTable(tableModel);
		scrollPane.setViewportView(table);

		tableModel.addColumn("Parameter");

		JPanel panel_2 = new JPanel();
		add(panel_2, BorderLayout.SOUTH);

		cancelButton = new JButton();
		panel_2.add(cancelButton);

		okButton = new JButton();
		panel_2.add(okButton);
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
		classField.setText(node.getClassName());
		int r = tableModel.getRowCount();
		for (int i=0; i< r; i++) 
			tableModel.removeRow(0);
		String[] args = node.getArguments();
		for (int i=0; i<node.getArguments().length; i++)
			tableModel.addRow(new String[] {args[i]});
	}
}
