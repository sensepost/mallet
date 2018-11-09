package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.net.SocketAddress;
import java.sql.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import org.jdesktop.swingx.JXTable;

import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.persistence.MessageDAO;

public class ConnectionPanel extends JPanel implements InterceptController {

	private ConnectionDataPanel cdp;

	private JTable table;
	private DefaultListModel<String> listModel = new DefaultListModel<>();

	private Map<String, ConnectionData> channelEventMap = new LinkedHashMap<>();

	private boolean intercept = false;
	private MessageDAO dao = null;

	private Preferences prefs = Preferences.userNodeForPackage(ConnectionPanel.class).node(ConnectionPanel.class.getSimpleName());

	public ConnectionPanel() {
		setLayout(new BorderLayout(0, 0));
		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.25);
		add(splitPane);

		JScrollPane scrollPane = new JScrollPane();
		splitPane.setLeftComponent(scrollPane);
		SplitPanePersistence spp = new SplitPanePersistence(prefs);
		spp.apply(splitPane, 200);
		splitPane.addPropertyChangeListener(spp);

		table = new JXTable(new ListTableModelAdapter(listModel)) {
			public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);

				String channelId = listModel.getElementAt(row);
				ConnectionData cd;
				synchronized (channelEventMap) {
					cd = channelEventMap.get(channelId);
				}
				if (table.getSelectedRow() == row) {
					c.setBackground(getSelectionBackground());
					c.setForeground(getSelectionForeground());
				} else if (cd.isException()) {
					c.setBackground(Color.PINK);
					c.setForeground(getForeground());
				} else if (cd.isClosed()) {
					c.setBackground(Color.LIGHT_GRAY);
					c.setForeground(getForeground());
				} else {
					c.setBackground(getBackground());
					c.setForeground(getForeground());
				}
				return c;
			}
		};
		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				ConnectionData cd = null;
				int selected = table.getSelectedRow();
				if (selected != -1) {
					selected = table.convertRowIndexToModel(selected);
					synchronized (channelEventMap) {
						String channelId = listModel.getElementAt(selected);
						cd = channelEventMap.get(channelId);
					}
				}
				cdp.setConnectionData(cd);
			}
		});
		table.setDefaultRenderer(Date.class, new DateRenderer(false));
		table.setAutoCreateRowSorter(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		scrollPane.setViewportView(table);

		cdp = new ConnectionDataPanel();
		splitPane.setRightComponent(cdp);

		TableColumnModelPersistence tcmp = new TableColumnModelPersistence(prefs, "column_widths");
		tcmp.apply(table.getColumnModel(), 75, 75, 75, 200, 800);
		table.getColumnModel().addColumnModelListener(tcmp);
	}

	public boolean isIntercept() {
		return intercept;
	}

	public void setIntercept(boolean intercept) {
		this.intercept = intercept;
		if (!intercept) {
			sendAllPendingEvents();
		}
	}

	public void setMessageDAO(MessageDAO dao) {
		this.dao = dao;
	}

	@Override
	public void addChannel(final String channelId, final SocketAddress localAddress,
			final SocketAddress remoteAddress) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized (channelEventMap) {
					ConnectionData cd = new ConnectionData(channelId, remoteAddress, localAddress);
					channelEventMap.put(channelId, cd);
					listModel.addElement(channelId);
				}
			}
		});
	}

	@Override
	public void linkChannels(final String channel1, final String channel2, final SocketAddress localAddress2,
			final SocketAddress remoteAddress2) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized (channelEventMap) {
					ConnectionData cd = channelEventMap.get(channel1);
					cd.setConnectedAddresses(channel2, localAddress2, remoteAddress2);
					channelEventMap.put(channel2, cd);
					// force update of the addresses in the UI
					int index = listModel.indexOf(channel1);
					listModel.set(index, channel1);
				}
			}
		});
	}

	@Override
	public void addChannelEvent(final ChannelEvent evt) throws Exception {
		if (evt instanceof ChannelReadEvent && dao != null) {
			((ChannelReadEvent) evt).setDao(dao);
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					addChannelEventEDT(evt);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void addChannelEventEDT(ChannelEvent evt) throws Exception {
		String cp = evt.getConnectionIdentifier();
		ConnectionData connectionData;
		synchronized (channelEventMap) {
			connectionData = channelEventMap.get(cp);
			connectionData.addChannelEvent(evt);
		}

		// force redraw of the element in the list
		int index = listModel.indexOf(cp);
		if (index > -1)
			listModel.setElementAt(listModel.getElementAt(index), index);

		if (!intercept) {
			try {
				connectionData.executeAllEvents();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void sendAllPendingEvents() {
		synchronized (channelEventMap) {
			for (String conn : channelEventMap.keySet()) {
				try {
					channelEventMap.get(conn).executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected static class AddrPair {
		private SocketAddress src, dst;

		public AddrPair(SocketAddress src, SocketAddress dst) {
			if (dst == null)
				throw new NullPointerException("dst");
			this.src = src;
			this.dst = dst;
		}

		public int hashCode() {
			return src.hashCode() ^ dst.hashCode();
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof AddrPair))
				return false;
			AddrPair that = (AddrPair) obj;
			return (this.src == that.src && this.dst == that.dst) || (this.src == that.dst && this.dst == that.src);
		}
	}

	private class ListTableModelAdapter extends AbstractTableModel implements ListDataListener {

		private ListModel<String> listModel = null;
		private String[] columnNames = new String[] { "Src", "Dst", "Events", "Opened", "Closed" };
		private Class<?>[] columnClasses = new Class<?>[] { SocketAddress.class, SocketAddress.class, String.class,
				Date.class, Date.class };

		public ListTableModelAdapter(ListModel<String> listModel) {
			setListModel(listModel);
		}

		public void setListModel(ListModel<String> listModel) {
			if (this.listModel == listModel)
				return;
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
			String channelId = listModel.getElementAt(rowIndex);
			ConnectionData cd;
			synchronized (channelEventMap) {
				cd = channelEventMap.get(channelId);
			}
			switch (columnIndex) {
			case 0:
				return cd.getRemoteAddress1();
			case 1:
				return cd.getRemoteAddress2() != null ? cd.getRemoteAddress2() : cd.getLocalAddress1();
			case 2:
				return cd.getPendingEventCount() + "/" + cd.getEventCount();
			case 3:
				return cd.getEventCount() == 0 ? null : new Date(cd.getEvents().getElementAt(0).getEventTime());
			case 4:
				return cd.isClosed() ? new Date(cd.getEvents().getElementAt(cd.getEventCount() - 1).getExecutionTime())
						: null;
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
