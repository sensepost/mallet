package com.sensepost.mallet.swing;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.ReferenceCountUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Date;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
import javax.swing.table.DefaultTableCellRenderer;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelExceptionEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ChannelUserEvent;
import com.sensepost.mallet.InterceptController.Direction;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.ObjectEditor;

public class ConnectionDataPanel extends JPanel {

	private final ListModel<ChannelEvent> EMPTY = new DefaultListModel<ChannelEvent>();
	private ListTableModelAdapter tableModel = new ListTableModelAdapter();

	private ConnectionData connectionData = null;
	private EditorController editorController = new EditorController();
	private ChannelEventRenderer channelEventRenderer = new ChannelEventRenderer();
	private DateRenderer dateRenderer = new DateRenderer();
	private DirectionRenderer directionRenderer = new DirectionRenderer();

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

		// ObjectEditor editor = new ByteBufEditor();
		ObjectEditor editor = new AutoEditor();
		editor.setEditorController(editorController);
		pendingPanel.add(editor.getEditorComponent(), BorderLayout.CENTER);

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
					if (editing != null && !editorController.isReadOnly()) {
						Object o = editorController.getObject();
						ReferenceCountUtil.retain(o);
						editing.setMessage(o);
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
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		scrollPane.setViewportView(table);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				if (editing != null && !editorController.isReadOnly()) {
					Object o = editorController.getObject();
					ReferenceCountUtil.retain(o);
					editing.setMessage(o);
				}
				int selectedRow = table.getSelectedRow();
				if (selectedRow != -1)
					selectedRow = table.convertRowIndexToModel(selectedRow);
				ChannelEvent evt;
				if (selectedRow < 0)
					evt = null;
				else
					evt = connectionData.getEvents().getElementAt(selectedRow);
				if (evt instanceof ChannelReadEvent) {
					editing = (ChannelReadEvent) evt;
					Object o = editing.getMessage();
					editorController.setObject(o);
					ReferenceCountUtil.release(o);
					editorController.setReadOnly(evt.isExecuted());
				} else if (evt instanceof ChannelExceptionEvent) {
					editing = null;
					editorController.setObject(((ChannelExceptionEvent) evt).getCause());
					editorController.setReadOnly(true);
				} else {
					editing = null;
					editorController.setObject(null);
					editorController.setReadOnly(true);
				}
			}
		});
		table.setDefaultRenderer(Date.class, dateRenderer);
		table.setDefaultRenderer(ChannelEvent.class, channelEventRenderer);
		table.setDefaultRenderer(Direction.class, directionRenderer);
		table.setAutoCreateRowSorter(true);
	}

	public void setConnectionData(ConnectionData connectionData) {
		if (this.connectionData == connectionData)
			return;

		this.connectionData = connectionData;

		if (connectionData != null) {
			tableModel.setListModel(connectionData.getEvents());
		} else {
			tableModel.setListModel(null);
		}
	}

	private class ChannelEventRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			if (value instanceof ChannelEvent) {
				ChannelEvent evt = (ChannelEvent) value;
				value = "";
				if (evt instanceof ChannelReadEvent) {
					Object o = ((ChannelReadEvent) evt).getMessage();
					if (o != null) {
						value = o.getClass().getName();
						if (o instanceof ByteBuf)
							value += " (" + ((ByteBuf) o).readableBytes() + " bytes)";
						else if (o instanceof byte[]) {
							value += " (" + ((byte[]) o).length + " bytes)";
						}
					}
					ReferenceCountUtil.release(o);
				} else if (evt instanceof ChannelUserEvent) {
					Object uevt = ((ChannelUserEvent) evt).getUserEvent();
					if (uevt != null) {
						if ((uevt instanceof ChannelInputShutdownEvent))
							value = "Input Shutdown";
						else
							value = "UserEvent " + uevt.toString();
					} else
						value += " UserEvent (null)";
				} else if (evt instanceof ChannelExceptionEvent) {
					String cause = ((ChannelExceptionEvent) evt).getCause();
					int cr = cause.indexOf('\n');
					if (cr != -1)
						cause = cause.substring(0, cr);
					value = cause;
				}
			}
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}

	}

	private class DirectionRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			if (value instanceof Direction) {
				Direction d = (Direction) value;
				if (d == Direction.Client_Server)
					value = "->";
				else
					value = "<-";
			}
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}

	}

	private class ListTableModelAdapter extends AbstractTableModel implements ListDataListener {

		private ListModel<ChannelEvent> listModel = null;
		private String[] columnNames = new String[] { "Received", "Sent", "Direction", "Event Type", "Value" };
		private Class<?>[] columnClasses = new Class<?>[] { Date.class, Date.class, Direction.class, String.class,
				ChannelEvent.class };

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
				return e;
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
			fireTableRowsUpdated(e.getIndex0(), e.getIndex1());
		}

	}
	
}
