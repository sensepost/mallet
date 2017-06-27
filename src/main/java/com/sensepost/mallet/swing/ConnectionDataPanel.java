package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Date;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelExceptionEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ChannelUserEvent;
import com.sensepost.mallet.InterceptController.Direction;
import com.sensepost.mallet.swing.editors.ByteBufEditor;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.ObjectEditor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.ChannelInputShutdownEvent;

public class ConnectionDataPanel extends JPanel {

	private final ListModel<ChannelEvent> EMPTY = new DefaultListModel<ChannelEvent>();
	private ListTableModelAdapter tableModel = new ListTableModelAdapter();

	private ConnectionData connectionData = null;
	private EditorController editorController = new EditorController();
	private ChannelEventRenderer channelEventRenderer = new ChannelEventRenderer();

	private ChannelReadEvent editing = null;
	private JTable table;

	public ConnectionDataPanel() {
		setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		add(splitPane, BorderLayout.CENTER);

		JPanel pendingPanel = new JPanel();
		pendingPanel.setLayout(new BorderLayout(0, 0));
		splitPane.setBottomComponent(pendingPanel);

//		ObjectEditor editor = new ByteBufEditor();
		ObjectEditor editor = new AutoEditor();
		editor.setEditorController(editorController);
		pendingPanel.add(editor.getComponent(), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		pendingPanel.add(buttonPanel, BorderLayout.SOUTH);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton dropButton = new JButton("Drop");
		dropButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int n = table.getSelectedRow();
				if (n >= 0) {
					try {
						connectionData.dropNextEvents(n);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					try {
						connectionData.dropNextEvent();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		buttonPanel.add(dropButton);

		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int n = table.getSelectedRow();
				if (n >= 0) {
					if (editing != null) {
						editing.setMessage(editorController.getObject());
						editing = null;
						editorController.setObject(null);
					}
					try {
						connectionData.executeNextEvents(n);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					try {
						connectionData.executeNextEvent();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		buttonPanel.add(sendButton);

		JScrollPane scrollPane = new JScrollPane();
		splitPane.setTopComponent(scrollPane);

		table = new JTable(tableModel);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		scrollPane.setViewportView(table);
		table.setModel(tableModel);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				if (editing != null) {
					editing.setMessage(editorController.getObject());
				}
				int selectedRow = table.getSelectedRow();
				ChannelEvent evt;
				if (selectedRow < 0)
					evt = null;
				else
					evt = connectionData.getEvents().getElementAt(selectedRow);
				if (evt instanceof ChannelReadEvent) {
					editing = (ChannelReadEvent) evt;
					editorController.setObject(editing.getMessage());
					editorController.setReadOnly(evt.isExecuted());
				} else if (evt instanceof ChannelExceptionEvent) {
					editing = null;
					editorController.setObject(((ChannelExceptionEvent)evt).getCause());
					editorController.setReadOnly(true);
				} else {
					editing = null;
					editorController.setObject(null);
					editorController.setReadOnly(true);
				}
			}
		});
	}

	public void setConnectionData(ConnectionData connectionData) {
		this.connectionData = connectionData;

		if (connectionData != null) {
			tableModel.setListModel(connectionData.getEvents());
		} else {
			tableModel.setListModel(null);
		}
	}

	private class ChannelEventRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			if (value instanceof ChannelEvent) {
				ChannelEvent evt = (ChannelEvent) value;
				value = evt.getDirection() == Direction.Client_Server ? " -> " : " <- ";
				if (evt instanceof ChannelActiveEvent) {
					value += " Open";
				} else if (evt instanceof ChannelReadEvent) {
					Object o = ((ChannelReadEvent) evt).getMessage();
					value += " Read: ";
					if (o != null) {
						value += o.getClass().getName();
						if (o instanceof ByteBuf)
							value += " (" + ((ByteBuf) o).readableBytes() + " bytes)";
						else if (o instanceof byte[]) {
							value += " (" + ((byte[]) o).length + " bytes)";
						}
					}
				} else if (evt instanceof ChannelInactiveEvent) {
					value += " Close";
				} else if (evt instanceof ChannelUserEvent) {
					Object uevt = ((ChannelUserEvent) evt).getUserEvent();
					if (uevt != null) {
						if ((uevt instanceof ChannelInputShutdownEvent))
							value += " Input Shutdown";
						else
							value += " UserEvent " + uevt.toString();
					} else
						value += " UserEvent (null)";
				}
			}
			return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		}

	}

	private class ListTableModelAdapter extends AbstractTableModel implements ListDataListener {

		private ListModel<ChannelEvent> listModel = null;
		private String[] columnNames = new String[] { "Received", "Sent", "Direction", "Event Type", "Value" };
		private Class<?>[] columnClasses = new Class<?>[] { Date.class, Date.class, Direction.class, String.class, Object.class };

		public void setListModel(ListModel<ChannelEvent> listModel) {
			if (this.listModel != null)
				this.listModel.removeListDataListener(this);
			this.listModel = listModel;
			if (this.listModel != null)
				this.listModel.addListDataListener(this);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return listModel == null ? 0 : listModel.getSize();
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnClasses[columnIndex];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (listModel == null || rowIndex > listModel.getSize())
				return null;
			ChannelEvent e = listModel.getElementAt(rowIndex);
			switch (columnIndex) {
			case 0:
				return new Date(e.getEventTime());
			case 1:
				return e.isExecuted() ? new Date(e.getExecutionTime()) : null;
			case 2:
				return e.getDirection();
			case 3:
				if (e instanceof ChannelActiveEvent)
					return "Open";
				else if (e instanceof ChannelInactiveEvent)
					return "Close";
				else if (e instanceof ChannelReadEvent)
					return "Read";
				else if (e instanceof ChannelExceptionEvent)
					return "Exception";
				else if (e instanceof ChannelUserEvent)
					return "Event";
				return "Unknown event";
			case 4:
				if (e instanceof ChannelReadEvent)
					return ((ChannelReadEvent) e).getMessage();
				else if (e instanceof ChannelExceptionEvent)
					return ((ChannelExceptionEvent) e).getCause();
				else if (e instanceof ChannelUserEvent)
					return ((ChannelUserEvent) e).getUserEvent();
			}
			return null;
		}

		@Override
		public void intervalAdded(ListDataEvent e) {
			fireTableRowsInserted(e.getIndex0(), e.getIndex1());
		}

		@Override
		public void intervalRemoved(ListDataEvent e) {
			fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
		}

		@Override
		public void contentsChanged(ListDataEvent e) {
			fireTableDataChanged();
		}

	}
}
